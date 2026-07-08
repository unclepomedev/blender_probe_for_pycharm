"""Dependency mounting for the in-Blender probe server.

Resolves an addon's Python dependencies for a Run/Debug session and puts them on
``sys.path`` so imports work exactly as they would once the extension is
installed: manifest-declared wheels are extracted and mounted, wheels for other
platforms are skipped, and a local ``.venv`` is offered as a fallback for
non-wheel projects.

This module is intentionally free of any ``bpy`` dependency -- it is pure
filesystem/string logic -- so it can be unit-tested under a plain interpreter
without a running Blender. ``probe_server.py`` extracts it alongside itself and
imports :func:`setup_dependencies`.
"""

import glob
import os
import platform
import shutil
import sys
import tomllib
import zipfile


def log(message):
    print(f"[BlenderProbe] {message}", flush=True)


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


def _read_manifest_wheels(manifest_path):
    """Return the wheel paths declared in the manifest's top-level ``wheels`` array.

    Returns a (possibly empty) list of the declared paths, or ``None`` when the
    manifest is missing or cannot be parsed. ``None`` means "fall back to scanning
    the wheels/ directory", so a manifest that is briefly invalid mid-edit doesn't
    block a Run/Debug. An empty list means "the manifest declares no wheels", which
    is faithful to Blender installing nothing.
    """
    if not os.path.isfile(manifest_path):
        return None

    try:
        with open(manifest_path, "rb") as fh:
            data = tomllib.load(fh)
    except (OSError, tomllib.TOMLDecodeError) as e:
        log(f"Could not parse {os.path.basename(manifest_path)}: {e}")
        return None

    wheels = data.get("wheels")
    if wheels is None:
        return []
    if not isinstance(wheels, list):
        log("Manifest 'wheels' is not a list; ignoring it.")
        return []
    return [w for w in wheels if isinstance(w, str)]


def _mount_wheels(wheel_paths, cache_root):
    """Extract each wheel into the cache and add the extracted dir to sys.path.

    Extracting (rather than appending the raw .whl) is what lets wheels with
    compiled extensions import: a native .so/.pyd cannot be loaded from inside a
    zip via zipimport. Wheels built for other platforms are skipped.
    """
    mounted = 0
    skipped = 0
    for whl in wheel_paths:
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


def _warn_unlisted_wheels(wheels_dir, mounted_paths):
    """Warn about .whl files in wheels/ that the manifest does not list.

    Blender only installs manifest-listed wheels, so an unlisted file would be
    absent once the extension is installed even though it is sitting in the folder.
    Surfacing it here catches that dev/prod divergence early.
    """
    if not os.path.isdir(wheels_dir):
        return

    mounted = {os.path.normpath(p) for p in mounted_paths}
    for whl in sorted(glob.glob(os.path.join(wheels_dir, "*.whl"))):
        if os.path.normpath(whl) not in mounted:
            log(
                "Wheel present but not listed in manifest 'wheels', so Blender "
                f"will not install it: {os.path.basename(whl)}"
            )


def _mount_venv_fallback(project_root):
    """Add a local ``.venv`` site-packages to sys.path (non-wheel projects)."""
    venv_lib = os.path.join(project_root, ".venv", "lib")
    if os.path.exists(venv_lib):
        for item in os.listdir(venv_lib):
            site_packages = os.path.join(venv_lib, item, "site-packages")
            if os.path.isdir(site_packages):
                if site_packages not in sys.path:
                    sys.path.append(site_packages)
                    log(f"Added local venv site-packages: {item}")
                    break


def setup_dependencies(project_root, addon_name):
    if not project_root or not addon_name:
        return

    addon_dir = os.path.join(project_root, addon_name)
    wheels_dir = os.path.join(addon_dir, "wheels")
    manifest_path = os.path.join(addon_dir, "blender_manifest.toml")
    cache_root = os.path.join(project_root, ".blender_probe", "wheels")

    declared = _read_manifest_wheels(manifest_path)

    if declared is None:
        # Manifest missing or unparsable: fall back to scanning wheels/ so a
        # transient edit can't block development.
        if os.path.isdir(wheels_dir):
            log("Manifest unavailable; falling back to scanning wheels/ directory.")
            _mount_wheels(sorted(glob.glob(os.path.join(wheels_dir, "*.whl"))), cache_root)
        else:
            _mount_venv_fallback(project_root)
        return

    # Manifest-driven: mount exactly what Blender would install, resolving each
    # declared path relative to the manifest.
    wheel_paths = []
    for entry in declared:
        resolved = os.path.normpath(os.path.join(addon_dir, entry))
        if os.path.isfile(resolved):
            wheel_paths.append(resolved)
        else:
            log(f"Manifest lists a wheel that is missing on disk: {entry}")

    if wheel_paths or os.path.isdir(wheels_dir):
        _warn_unlisted_wheels(wheels_dir, wheel_paths)
        _mount_wheels(wheel_paths, cache_root)
    else:
        # Nothing bundled and no wheels/ dir: offer the local venv as a dev aid.
        _mount_venv_fallback(project_root)
