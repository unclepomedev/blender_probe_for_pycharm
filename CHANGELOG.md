# Blender Probe Changelog

## [Unreleased]

## [0.0.2] - 2026-01-08

### Improved
- **Stub Generation Architecture**: Completely overhauled the generation process to split type definitions into individual `.pyi` files per class. This resolves PyCharm's file size limit warnings (Code Insight not available) and significantly improves indexing performance.
- **Project Integration**: The generated `.blender_stubs` directory is now automatically marked as a "Source Root" in PyCharm, enabling immediate autocompletion without manual configuration.

### Fixed
- **Submodule Resolution**: Fixed an issue where dynamically defined submodules (e.g., `gpu_extras.batch`, `mathutils.geometry`) were missing from the stubs.
- **Type Definitions**:
    - Correctly implemented Generics for `bpy_prop_collection` for better type inference (e.g., `bpy.data.objects['Cube']` now correctly infers `Object`).
    - Fixed missing `Optional` return types and imports.
    - Added proper definitions for `bpy.app.handlers.persistent` and `bpy.props` functions.

## [0.0.1] - 2026-01-07

### Added
- **Blender API Autocompletion**: Automatically generates Python type stubs (`.pyi`) for `bpy`, `mathutils`, and `bmesh` from a running Blender instance.
- **Integrated Test Runner**: Run standard Python `unittest` suites inside Blender directly from PyCharm UI.
- **Visual Test Feedback**: Support for PyCharm's native test runner UI (green/red bars, tree view).
- **Clean Environment**: Tests execute with `--factory-startup` to ensure reproducibility.
- **Configurable Executable**: Support for Windows, macOS, and Linux Blender paths.
- **Blender Support**: Compatible with Blender 4.2+ and 5.x.