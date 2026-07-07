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


def _write_manifest(addon_dir, wheels, raw=None):
    """Write a blender_manifest.toml. `wheels=None` omits the array entirely;
    `raw` writes arbitrary content verbatim (used to test malformed TOML)."""
    path = os.path.join(str(addon_dir), "blender_manifest.toml")
    if raw is not None:
        content = raw
    else:
        lines = ['schema_version = "1.0.0"', 'id = "myaddon"']
        if wheels is not None:
            listed = "".join(f'    "{w}",\n' for w in wheels)
            lines.append(f"wheels = [\n{listed}]")
        content = "\n".join(lines) + "\n"
    with open(path, "w") as fh:
        fh.write(content)
    return path


@pytest.fixture
def isolate_imports():
    """Restore sys.path and sys.modules so wheels imported in a test don't leak."""
    saved_path = list(sys.path)
    saved_modules = set(sys.modules)
    yield
    sys.path[:] = saved_path
    for mod in set(sys.modules) - saved_modules:
        del sys.modules[mod]


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


# --- manifest parsing ---------------------------------------------------------


def test_read_manifest_wheels_parses_declared_list(probe, tmp_path):
    manifest = _write_manifest(tmp_path, ["./wheels/a.whl", "./wheels/b.whl"])
    assert probe._read_manifest_wheels(manifest) == ["./wheels/a.whl", "./wheels/b.whl"]


def test_read_manifest_wheels_missing_file_returns_none(probe, tmp_path):
    # None -> caller falls back to scanning the wheels/ directory.
    assert probe._read_manifest_wheels(os.path.join(str(tmp_path), "nope.toml")) is None


def test_read_manifest_wheels_absent_array_returns_empty(probe, tmp_path):
    # A parsed manifest with no wheels key means Blender installs nothing.
    manifest = _write_manifest(tmp_path, None)
    assert probe._read_manifest_wheels(manifest) == []


def test_read_manifest_wheels_malformed_returns_none(probe, tmp_path, capsys):
    manifest = _write_manifest(tmp_path, None, raw="this is = not valid toml [[[\n")
    assert probe._read_manifest_wheels(manifest) is None
    assert "Could not parse" in capsys.readouterr().out


# --- end to end ---------------------------------------------------------------


def test_setup_dependencies_mounts_only_listed_wheels(probe, tmp_path, isolate_imports, capsys):
    addon = tmp_path / "myaddon"
    wheels = addon / "wheels"
    wheels.mkdir(parents=True)
    _make_wheel(wheels, "listed-1.0-py3-none-any.whl", {"listed_pkg/__init__.py": "V = 1\n"})
    _make_wheel(wheels, "extra-1.0-py3-none-any.whl", {"extra_pkg/__init__.py": "V = 2\n"})
    _write_manifest(addon, ["./wheels/listed-1.0-py3-none-any.whl"])

    probe.setup_dependencies(str(tmp_path), "myaddon")

    import listed_pkg

    assert listed_pkg.V == 1

    extracted = [p.name for p in (tmp_path / ".blender_probe" / "wheels").iterdir()]
    assert any(n.startswith("listed-1.0") for n in extracted)
    # The unlisted wheel is neither extracted nor importable, and it is flagged.
    assert not any(n.startswith("extra-1.0") for n in extracted)
    with pytest.raises(ImportError):
        import extra_pkg  # noqa: F401
    out = capsys.readouterr().out
    assert "not listed in manifest" in out
    assert "extra-1.0-py3-none-any.whl" in out


def test_setup_dependencies_applies_platform_filter_to_listed(
    probe, tmp_path, monkeypatch, isolate_imports
):
    monkeypatch.setattr(probe.sys, "platform", "linux")
    monkeypatch.setattr(probe.platform, "machine", lambda: "x86_64")

    addon = tmp_path / "myaddon"
    wheels = addon / "wheels"
    wheels.mkdir(parents=True)
    _make_wheel(wheels, "purepkg-1.0-py3-none-any.whl", {"purepkg/__init__.py": "V = 1\n"})
    _make_wheel(
        wheels,
        "maconly-1.0-cp311-cp311-macosx_11_0_arm64.whl",
        {"macpkg/__init__.py": "V = 1\n"},
    )
    _write_manifest(
        addon,
        [
            "./wheels/purepkg-1.0-py3-none-any.whl",
            "./wheels/maconly-1.0-cp311-cp311-macosx_11_0_arm64.whl",
        ],
    )

    probe.setup_dependencies(str(tmp_path), "myaddon")

    extracted = [p.name for p in (tmp_path / ".blender_probe" / "wheels").iterdir()]
    assert any(n.startswith("purepkg-1.0") for n in extracted)
    # Listed but for another platform -> skipped on linux, exactly as Blender would.
    assert not any(n.startswith("maconly-1.0") for n in extracted)


def test_setup_dependencies_warns_on_missing_listed_wheel(probe, tmp_path, capsys):
    addon = tmp_path / "myaddon"
    addon.mkdir(parents=True)
    _write_manifest(addon, ["./wheels/ghost-1.0-py3-none-any.whl"])

    probe.setup_dependencies(str(tmp_path), "myaddon")  # must not raise

    assert "missing on disk" in capsys.readouterr().out
    assert not (tmp_path / ".blender_probe").exists()


def test_setup_dependencies_without_manifest_falls_back_to_glob(
    probe, tmp_path, monkeypatch, isolate_imports, capsys
):
    monkeypatch.setattr(probe.sys, "platform", "linux")
    monkeypatch.setattr(probe.platform, "machine", lambda: "x86_64")

    # No manifest at all -> scan wheels/ so development isn't blocked.
    wheels = tmp_path / "myaddon" / "wheels"
    wheels.mkdir(parents=True)
    _make_wheel(wheels, "fallbackpkg-1.0-py3-none-any.whl", {"fallbackpkg/__init__.py": "V = 7\n"})
    _make_wheel(
        wheels,
        "wrongplat-1.0-cp311-cp311-macosx_11_0_arm64.whl",
        {"wrongplat/__init__.py": "x = 1\n"},
    )

    probe.setup_dependencies(str(tmp_path), "myaddon")

    import fallbackpkg

    assert fallbackpkg.V == 7
    assert "falling back to scanning" in capsys.readouterr().out
    extracted = [p.name for p in (tmp_path / ".blender_probe" / "wheels").iterdir()]
    assert not any(n.startswith("wrongplat") for n in extracted)  # still platform-filtered


def test_setup_dependencies_parse_error_falls_back_to_glob(
    probe, tmp_path, isolate_imports, capsys
):
    addon = tmp_path / "myaddon"
    wheels = addon / "wheels"
    wheels.mkdir(parents=True)
    _make_wheel(wheels, "brokenmani-1.0-py3-none-any.whl", {"brokenmani/__init__.py": "V = 3\n"})
    _write_manifest(addon, None, raw="wheels = [ this is broken\n")

    probe.setup_dependencies(str(tmp_path), "myaddon")

    import brokenmani

    assert brokenmani.V == 3
    out = capsys.readouterr().out
    assert "Could not parse" in out
    assert "falling back to scanning" in out


def test_setup_dependencies_noop_without_args(probe):
    # Missing project root / addon name must not raise.
    probe.setup_dependencies("", "")
    probe.setup_dependencies(None, None)
