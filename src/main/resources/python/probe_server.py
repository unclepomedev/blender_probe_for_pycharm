import glob
import importlib
import json
import os
import platform
import queue
import shutil
import socket
import sys
import threading
import traceback
import zipfile

import bpy

# When Blender is launched as a subprocess its stdout is a pipe, not a TTY, so
# Python block-buffers it. That makes addon print() output invisible until
# something flushes the buffer (e.g. a reload). Force line buffering so prints
# appear live. This is a best-effort tweak: a replaced/older/non-standard stream
# may lack reconfigure() or reject the kwarg, so catch everything rather than let
# a failure here abort import and take down the timer/socket registration.
try:
    sys.stdout.reconfigure(line_buffering=True)
    sys.stderr.reconfigure(line_buffering=True)
except Exception:
    pass

HOST = "127.0.0.1"
HEADER_SIZE = 64

execution_queue = queue.Queue()
server_running = False


def log(message):
    print(f"[BlenderProbe] {message}", flush=True)


def start_socket_server():
    global server_running
    log("Debug: Socket thread started.")

    try:
        server_socket = socket.socket(socket.AF_INET, socket.SOCK_STREAM)
        server_socket.setsockopt(socket.SOL_SOCKET, socket.SO_REUSEADDR, 1)

        log("Attempting to bind socket...")

        server_socket.bind((HOST, 0))
        server_socket.listen()
        server_running = True

        port = server_socket.getsockname()[1]

        print(f"BLENDER_PROBE_PORT::{port}", flush=True)
        log(f"Server listening on {HOST}:{port}")

    except Exception as e:
        log(f"FATAL ERROR in Socket Thread: {e}")
        traceback.print_exc()
        return

    while server_running:
        try:
            conn, addr = server_socket.accept()
            thread = threading.Thread(target=handle_client, args=(conn, addr))
            thread.daemon = True
            thread.start()
        except Exception as e:
            log(f"Socket accept error: {e}")
            break


# Marker file dropped inside each extracted-wheel directory. It stores a
# signature of the source .whl (size + mtime); a matching marker means the exact
# same wheel is already unpacked, so extraction can be skipped on the next launch.
_WHEEL_MARKER = ".blender_probe_extracted"


def _current_os_family():
    if sys.platform == "darwin":
        return "macos"
    if sys.platform.startswith("linux"):
        return "linux"
    if sys.platform.startswith("win"):
        return "windows"
    return None


def _tag_os_family(platform_tag):
    """Map a wheel platform tag to an OS family, or None if unrecognized."""
    tag = platform_tag.lower()
    if tag.startswith("macosx") or tag == "macos":
        return "macos"
    if "linux" in tag:  # linux_*, manylinux*, musllinux*
        return "linux"
    if tag.startswith("win"):  # win32, win_amd64, win_arm64
        return "windows"
    return None


def _arch_matches(platform_tag, os_family):
    """Whether a platform tag's CPU arch matches this machine.

    Conservative by design: an arch we don't recognize returns True, so we never
    skip a wheel that might actually be needed on this machine.
    """
    tag = platform_tag.lower()
    machine = platform.machine().lower()  # x86_64 / arm64 / aarch64 / amd64 / ...

    if os_family == "macos":
        if tag.endswith("universal2") or tag.endswith("intel"):
            return True
        if machine == "arm64":
            return tag.endswith("arm64")
        if machine in ("x86_64", "amd64"):
            return tag.endswith("x86_64")
        return True

    if os_family == "linux":
        if machine in ("x86_64", "amd64"):
            return tag.endswith("x86_64")
        if machine in ("aarch64", "arm64"):
            return tag.endswith("aarch64")
        if machine in ("i686", "i386", "x86"):
            return tag.endswith("i686") or tag.endswith("i386")
        return True

    if os_family == "windows":
        if machine in ("amd64", "x86_64"):
            return tag == "win_amd64"
        if machine in ("arm64", "aarch64"):
            return tag == "win_arm64"
        if machine in ("x86", "i386", "i686"):
            return tag == "win32"
        return True

    return True


def _platform_tag_matches(platform_tag):
    family = _tag_os_family(platform_tag)
    if family is None:
        # Unknown platform family: don't risk skipping a wheel we can't judge.
        return True
    if family != _current_os_family():
        return False
    return _arch_matches(platform_tag, family)


def _wheel_platform_tags(filename):
    """Return the platform tags encoded in a wheel filename, or None.

    Wheel names are ``name-version(-build)?-python-abi-platform.whl`` (PEP 427);
    each of the last three fields may itself be a dot-separated set of tags.
    """
    name = os.path.basename(filename)
    if name.lower().endswith(".whl"):
        name = name[:-4]
    parts = name.split("-")
    if len(parts) < 5:
        return None
    return parts[-1].split(".")


def _wheel_is_compatible(filename):
    """True if this wheel should be used on the current platform.

    Pure-Python wheels (platform tag ``any``) are always compatible; unparsable
    names default to compatible so we never silently drop a wheel we can't judge.
    """
    platform_tags = _wheel_platform_tags(filename)
    if platform_tags is None:
        return True
    if any(tag == "any" for tag in platform_tags):
        return True
    return any(_platform_tag_matches(tag) for tag in platform_tags)


def _wheel_signature(whl_path):
    try:
        st = os.stat(whl_path)
    except OSError:
        return ""
    return f"{st.st_size}:{int(st.st_mtime)}"


def _extract_wheel(whl_path, cache_root):
    """Extract a wheel into a per-wheel cache dir and return that dir (or None).

    Skips extraction when the identical wheel (same size + mtime) is already
    unpacked, so repeated launches don't re-unzip unchanged dependencies. The
    cache key is the wheel's filename, which encodes name + exact version +
    platform, so a new version lands in its own directory.
    """
    stem = os.path.basename(whl_path)
    if stem.lower().endswith(".whl"):
        stem = stem[:-4]

    dest = os.path.join(cache_root, stem)
    marker = os.path.join(dest, _WHEEL_MARKER)
    signature = _wheel_signature(whl_path)

    if signature and os.path.isfile(marker):
        try:
            with open(marker, encoding="utf-8") as fh:
                if fh.read().strip() == signature:
                    log(f"Using cached wheel: {stem}")
                    return dest
        except OSError:
            pass  # unreadable marker -> fall through and re-extract cleanly

    # Missing, partial, or stale extraction: start from a clean directory.
    if os.path.isdir(dest):
        shutil.rmtree(dest, ignore_errors=True)

    try:
        os.makedirs(dest, exist_ok=True)
        with zipfile.ZipFile(whl_path) as zf:
            zf.extractall(dest)
        with open(marker, "w", encoding="utf-8") as fh:
            fh.write(signature)
    except Exception as e:
        log(f"Failed to extract wheel {stem}: {e}")
        shutil.rmtree(dest, ignore_errors=True)
        return None

    log(f"Extracted wheel: {stem}")
    return dest


def setup_dependencies(project_root, addon_name):
    if not project_root or not addon_name:
        return

    wheels_dir = os.path.join(project_root, addon_name, "wheels")

    if os.path.exists(wheels_dir):
        log(f"Found wheels directory: {wheels_dir}")
        whl_files = sorted(glob.glob(os.path.join(wheels_dir, "*.whl")))

        # Extract wheels into a persistent, project-local cache and put the
        # extracted directories on sys.path. Appending the raw .whl (a zip) only
        # works for pure-Python packages via zipimport; native extension modules
        # (.so/.pyd) cannot be loaded from inside a zip. Extracting first mirrors
        # how Blender installs extension wheels and makes both kinds importable.
        cache_root = os.path.join(project_root, ".blender_probe", "wheels")

        mounted = 0
        skipped = 0
        for whl in whl_files:
            if not _wheel_is_compatible(whl):
                log(f"Skipping wheel for another platform: {os.path.basename(whl)}")
                skipped += 1
                continue

            dest = _extract_wheel(whl, cache_root)
            if dest and dest not in sys.path:
                sys.path.append(dest)
                mounted += 1

        if mounted:
            log(f"Mounted {mounted} wheel(s) for development.")
        if skipped:
            log(f"Skipped {skipped} wheel(s) not matching this platform.")
    else:
        venv_lib = os.path.join(project_root, ".venv", "lib")
        if os.path.exists(venv_lib):
            for item in os.listdir(venv_lib):
                site_packages = os.path.join(venv_lib, item, "site-packages")
                if os.path.isdir(site_packages):
                    if site_packages not in sys.path:
                        sys.path.append(site_packages)
                        log(f"Added local venv site-packages: {item}")
                        break


def handle_client(conn, addr):
    """
    Handles a single connection via simple JSON messaging.
    Protocol: Header(Length of JSON, 64bytes) + JSON Body
    """
    try:
        msg_length_raw = conn.recv(HEADER_SIZE).decode("utf-8")
        if not msg_length_raw:
            return

        try:
            msg_length = int(msg_length_raw.strip())
        except ValueError:
            log("Invalid message length header received.")
            return

        if msg_length > 10 * 1024 * 1024:
            log(f"Message too large: {msg_length} bytes. Dropping connection.")
            return

        data = b""
        while len(data) < msg_length:
            packet = conn.recv(msg_length - len(data))
            if not packet:
                break
            data += packet

        message = json.loads(data.decode("utf-8"))
        execution_queue.put(message)
        conn.sendall(b"ACK")

    except Exception as e:
        log(f"Error handling client: {e}")
        traceback.print_exc()
    finally:
        conn.close()


def main_thread_loop():
    """
    Checks the queue for pending actions and executes them in the main thread.
    """
    while not execution_queue.empty():
        try:
            cmd = execution_queue.get_nowait()
        except queue.Empty:
            break
        # Never let a failing command propagate out of the timer; if it did,
        # Blender would unregister the timer and the queue would stop draining.
        try:
            process_command(cmd)
        except Exception as e:
            log(f"Error processing command: {e}")
            traceback.print_exc()
    return 0.1


def deep_reload_addon(module_name):
    """
    Performs a deep reload of the addon.
    """
    log(f"Deep Reload for: {module_name}")

    # Try to Unregister existing
    if module_name in sys.modules:
        try:
            mod = sys.modules[module_name]
            if hasattr(mod, "unregister"):
                try:
                    mod.unregister()
                    log("Unregistered successfully.")
                except Exception as e:
                    log(f"Warning during unregister: {e}")
        except Exception as e:
            log(f"Error accessing module for unregister: {e}")

    # Purge from sys.modules (The Magic Step)
    keys_to_purge = [
        k
        for k in sys.modules.keys()
        if k == module_name or k.startswith(module_name + ".")
    ]

    for key in keys_to_purge:
        del sys.modules[key]

    log(f"Purged {len(keys_to_purge)} modules from memory.")

    # Re-import and Register
    try:
        new_mod = importlib.import_module(module_name)
        if hasattr(new_mod, "register"):
            new_mod.register()
            log(f"Re-registered {module_name} successfully!")
        else:
            log(f"Warning: {module_name} has no register() function.")

    except Exception as e:
        log(f"CRITICAL FAIL during re-import: {e}")
        traceback.print_exc()


def process_command(cmd):
    action = cmd.get("action")

    if action == "ping":
        log("Pong! (Received Ping)")

    elif action == "reload":
        module_name = cmd.get("module_name")
        if module_name:
            deep_reload_addon(module_name)
        else:
            log("Reload command received but no module_name specified.")


def enable_dev_addon():
    """
    Attempts to enable the addon specified in environment variables.
    """
    addon_name = os.environ.get("BLENDER_PROBE_ADDON_NAME")
    if not addon_name:
        return

    log(f"Auto-enabling dev addon: {addon_name}")
    try:
        if addon_name not in bpy.context.preferences.addons:
            bpy.ops.preferences.addon_enable(module=addon_name)
            log(f"Successfully enabled addon: {addon_name}")
        else:
            log(f"Addon {addon_name} is already enabled.")
    except Exception as e:
        log(f"Failed to enable addon {addon_name}: {e}")
        traceback.print_exc()


def attach_to_debugger():
    debug_port = os.environ.get("BLENDER_PROBE_DEBUG_PORT")
    pydevd_path = os.environ.get("BLENDER_PROBE_PYDEVD_PATH")

    if debug_port and pydevd_path:
        log(f"Attempting to attach debugger at port {debug_port}...")

        if pydevd_path not in sys.path:
            sys.path.append(pydevd_path)

        try:
            import pydevd

            pydevd.settrace(
                "127.0.0.1",
                port=int(debug_port),
                suspend=False,
            )
            log("Debugger attached successfully!")
        except Exception as e:
            log(f"Failed to attach debugger: {e}")
            traceback.print_exc()


def register():
    log("Register function called.")

    attach_to_debugger()
    project_root = os.environ.get("BLENDER_PROBE_PROJECT_ROOT")
    addon_name = os.environ.get("BLENDER_PROBE_ADDON_NAME")
    if project_root and project_root not in sys.path:
        sys.path.append(project_root)
        log(f"Added project root to sys.path: {project_root}")
    setup_dependencies(project_root, addon_name)

    try:
        server_thread = threading.Thread(target=start_socket_server, daemon=True)
        server_thread.start()
        log("Server thread start command issued.")
    except Exception as e:
        log(f"Failed to start thread: {e}")
        traceback.print_exc()

    if not bpy.app.timers.is_registered(main_thread_loop):
        # persistent=True keeps the timer alive across .blend file loads.
        # Without it, opening or creating a file unregisters the timer, so the
        # queue stops draining and reload/ping commands are silently ignored.
        bpy.app.timers.register(main_thread_loop, persistent=True)
        log("Main thread timer registered.")

    if addon_name:
        bpy.app.timers.register(enable_dev_addon, first_interval=0.5)


if __name__ == "__main__":
    try:
        register()
    except Exception as e:
        print(f"CRITICAL ERROR at script toplevel: {e}", flush=True)
        traceback.print_exc()
