import bpy
import threading
import socket
import json
import queue
import sys
import os
import traceback
import time
import importlib
import glob

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


def setup_dependencies(project_root, addon_name):
    if not project_root or not addon_name:
        return

    wheels_dir = os.path.join(project_root, addon_name, "wheels")

    if os.path.exists(wheels_dir):
        log(f"Found wheels directory: {wheels_dir}")
        whl_files = glob.glob(os.path.join(wheels_dir, "*.whl"))

        count = 0
        for whl in whl_files:
            if whl not in sys.path:
                sys.path.append(whl)
                log(f"Added dependency to path: {os.path.basename(whl)}")
                count += 1

        if count > 0:
            log(f"Mounted {count} wheels for development.")
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
            process_command(cmd)
        except queue.Empty:
            break
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
        bpy.app.timers.register(main_thread_loop)
        log("Main thread timer registered.")

    if addon_name:
        bpy.app.timers.register(enable_dev_addon, first_interval=0.5)


if __name__ == "__main__":
    try:
        register()
    except Exception as e:
        print(f"CRITICAL ERROR at script toplevel: {e}", flush=True)
        traceback.print_exc()
