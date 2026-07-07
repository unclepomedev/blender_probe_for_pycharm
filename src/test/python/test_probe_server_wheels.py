"""Tests for wheel dependency handling in the probe server.

Modern Blender extensions bundle their Python dependencies as wheels next to the
manifest. These tests cover the three concerns the probe server has to get right:

* extraction (a raw .whl on sys.path only imports pure-Python packages; native
  extensions must be unpacked first),
* caching (don't re-unzip an already-extracted, unchanged wheel), and
* platform filtering (a bundle may carry wheels for several OS/arch combos).
"""

import os
import sys
import zipfile

import pytest


def _make_wheel(dirpath, name, files):
    """Create a minimal wheel-shaped zip and return its path."""
    path = os.path.join(str(dirpath), name)
    with zipfile.ZipFile(path, "w") as z:
        for arcname, content in files.items():
            z.writestr(arcname, content)
    return path


# --- filename parsing ---------------------------------------------------------


@pytest.mark.parametrize(
    "filename, expected",
    [
        ("foo-1.0-py3-none-any.whl", ["any"]),
        ("foo-1.0-cp311-cp311-manylinux2014_x86_64.whl", ["manylinux2014_x86_64"]),
        # optional build tag between version and python tag
        ("foo-1.0-1-cp311-cp311-win_amd64.whl", ["win_amd64"]),
        # compressed (dot-separated) platform tag set
        (
            "foo-1.0-cp311-cp311-macosx_10_9_x86_64.macosx_11_0_arm64.whl",
            ["macosx_10_9_x86_64", "macosx_11_0_arm64"],
        ),
        # not a wheel name
        ("not-a-wheel.whl", None),
    ],
)
def test_wheel_platform_tags(probe, filename, expected):
    assert probe._wheel_platform_tags(filename) == expected


# --- platform compatibility ---------------------------------------------------


@pytest.mark.parametrize(
    "sys_platform, machine, filename, compatible",
    [
        # linux x86_64
        ("linux", "x86_64", "foo-1.0-py3-none-any.whl", True),
        ("linux", "x86_64", "foo-1.0-cp311-cp311-manylinux2014_x86_64.whl", True),
        ("linux", "x86_64", "foo-1.0-cp311-cp311-musllinux_1_1_x86_64.whl", True),
        ("linux", "x86_64", "foo-1.0-cp311-cp311-manylinux2014_aarch64.whl", False),
        ("linux", "x86_64", "foo-1.0-cp311-cp311-macosx_11_0_arm64.whl", False),
        ("linux", "x86_64", "foo-1.0-cp311-cp311-win_amd64.whl", False),
        # macOS arm64
        ("darwin", "arm64", "foo-1.0-cp311-cp311-macosx_11_0_arm64.whl", True),
        ("darwin", "arm64", "foo-1.0-cp311-cp311-macosx_11_0_universal2.whl", True),
        ("darwin", "arm64", "foo-1.0-cp311-cp311-macosx_10_9_x86_64.whl", False),
        (
            "darwin",
            "arm64",
            "foo-1.0-cp311-cp311-macosx_10_9_x86_64.macosx_11_0_arm64.whl",
            True,
        ),
        ("darwin", "arm64", "foo-1.0-cp311-cp311-manylinux2014_aarch64.whl", False),
        # windows amd64 (platform.machine() reports "AMD64")
        ("win32", "AMD64", "foo-1.0-cp311-cp311-win_amd64.whl", True),
        ("win32", "AMD64", "foo-1.0-cp311-cp311-win32.whl", False),
        ("win32", "AMD64", "foo-1.0-cp311-cp311-win_arm64.whl", False),
        ("win32", "AMD64", "foo-1.0-py3-none-any.whl", True),
    ],
)
def test_wheel_is_compatible(probe, monkeypatch, sys_platform, machine, filename, compatible):
    monkeypatch.setattr(probe.sys, "platform", sys_platform)
    monkeypatch.setattr(probe.platform, "machine", lambda: machine)
    assert probe._wheel_is_compatible(filename) is compatible


def test_unparsable_name_defaults_to_compatible(probe):
    # We must not silently drop a wheel just because its name is unusual.
    assert probe._wheel_is_compatible("weird.whl") is True


# --- extraction + caching -----------------------------------------------------


def test_extract_wheel_unpacks_contents(probe, tmp_path):
    whl = _make_wheel(tmp_path, "pkg-1.0-py3-none-any.whl", {"pkg/__init__.py": "V = 1\n"})
    cache = str(tmp_path / "cache")

    dest = probe._extract_wheel(whl, cache)

    assert dest is not None
    assert os.path.isfile(os.path.join(dest, "pkg", "__init__.py"))
    assert os.path.isfile(os.path.join(dest, probe._WHEEL_MARKER))


def test_extract_wheel_is_cached(probe, tmp_path, monkeypatch):
    whl = _make_wheel(tmp_path, "pkg-1.0-py3-none-any.whl", {"pkg/__init__.py": "V = 1\n"})
    cache = str(tmp_path / "cache")

    zip_opens = {"count": 0}
    real_zipfile = probe.zipfile.ZipFile

    def counting_zipfile(*args, **kwargs):
        zip_opens["count"] += 1
        return real_zipfile(*args, **kwargs)

    monkeypatch.setattr(probe.zipfile, "ZipFile", counting_zipfile)

    first = probe._extract_wheel(whl, cache)
    second = probe._extract_wheel(whl, cache)

    assert first == second
    assert zip_opens["count"] == 1  # the second call hit the cache


def test_extract_wheel_reextracts_when_wheel_changes(probe, tmp_path, monkeypatch):
    name = "pkg-1.0-py3-none-any.whl"
    whl = _make_wheel(tmp_path, name, {"pkg/__init__.py": "V = 1\n"})
    cache = str(tmp_path / "cache")

    probe._extract_wheel(whl, cache)

    # Rebuild the wheel with different content (changes size) and a later mtime,
    # simulating a developer rebuilding the same-named wheel.
    whl = _make_wheel(tmp_path, name, {"pkg/__init__.py": "V = 999  # rebuilt\n"})
    os.utime(whl, (os.stat(whl).st_atime + 100, os.stat(whl).st_mtime + 100))

    dest = probe._extract_wheel(whl, cache)

    with open(os.path.join(dest, "pkg", "__init__.py")) as fh:
        assert "999" in fh.read()  # stale cache was refreshed


# --- end to end ---------------------------------------------------------------


def test_setup_dependencies_extracts_only_compatible(probe, tmp_path, monkeypatch):
    monkeypatch.setattr(probe.sys, "platform", "linux")
    monkeypatch.setattr(probe.platform, "machine", lambda: "x86_64")

    wheels = tmp_path / "myaddon" / "wheels"
    wheels.mkdir(parents=True)
    _make_wheel(wheels, "purepkg-2.5.0-py3-none-any.whl", {"purepkg/__init__.py": "VALUE = 42\n"})
    _make_wheel(
        wheels,
        "wrongplat-1.0-cp311-cp311-macosx_11_0_arm64.whl",
        {"wrongplat/__init__.py": "x = 1\n"},
    )

    saved_path = list(sys.path)
    saved_modules = set(sys.modules)
    try:
        probe.setup_dependencies(str(tmp_path), "myaddon")

        # The pure-Python wheel is extracted and importable.
        import purepkg

        assert purepkg.VALUE == 42

        cache = tmp_path / ".blender_probe" / "wheels"
        extracted = [p.name for p in cache.iterdir()]
        assert any(n.startswith("purepkg-2.5.0") for n in extracted)
        # The macOS-only wheel is skipped on linux and never extracted.
        assert not any(n.startswith("wrongplat") for n in extracted)
    finally:
        sys.path[:] = saved_path
        for mod in set(sys.modules) - saved_modules:
            del sys.modules[mod]


def test_setup_dependencies_noop_without_args(probe):
    # Missing project root / addon name must not raise.
    probe.setup_dependencies("", "")
    probe.setup_dependencies(None, None)
