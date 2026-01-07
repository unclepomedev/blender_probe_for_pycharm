# Blender Probe Changelog

## [Unreleased]

## [0.0.1] - 2026-01-07

### Added
- **Blender API Autocompletion**: Automatically generates Python type stubs (`.pyi`) for `bpy`, `mathutils`, and `bmesh` from a running Blender instance.
- **Integrated Test Runner**: Run standard Python `unittest` suites inside Blender directly from PyCharm UI.
- **Visual Test Feedback**: Support for PyCharm's native test runner UI (green/red bars, tree view).
- **Clean Environment**: Tests execute with `--factory-startup` to ensure reproducibility.
- **Configurable Executable**: Support for Windows, macOS, and Linux Blender paths.
- **Blender Support**: Compatible with Blender 4.2+ and 5.x.