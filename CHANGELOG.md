# Blender Probe Changelog

## [Unreleased]

### Changed

* **Stub Generation Enhancement**: 
  * Enhanced stub generation with support for dynamic collections, iterables, and arithmetic operators.
  * Adds the initial stub files for BMEdge, BMFace, BMLoop, and BMVert.
  * Adds `__contains__` to `bpy_prop_collection` stubs
  * Improved detection capabilities for Blender's built-in API documentation and property descriptions.
  * Enhanced type support with `Literal`, `Optional`, `Annotated`, and `@property`.

## [0.1.3] - 2026-01-27

### Changed

* **Test Execution Fix**: Fixed issues where stale pycache directories interfered with test results.

## [0.1.2] - 2026-01-25

### Changed

* **Stub Generation Enhancement**: Improved stub generation logic to support detailed type for return values.
* **Build Targets**: Explicitly targeted PyCharm to resolve marketplace verification warnings.

## [0.1.1] - 2026-01-22

### Changed

* **Blender Path Detection**: Enhanced path auto-detection logic to support projects managing versions via [blup](https://github.com/unclepomedev/blup).

### Fixed

* **UI Freezes (EDT Violation)**: Rewrote the Run/Debug/Test runners to execute asynchronously, eliminating UI freezes.
* **Internal API Usage**: Removed dependencies on JetBrains internal APIs (`StartupManager`).

## [0.1.0] - 2026-01-14

### 🚀 Initial Release (Public Beta)

**Blender Probe** is now available in Public Beta!
This plugin bridges the gap between PyCharm and Blender, offering a seamless development experience for addon creators.

#### ✨ Key Features
* **Dynamic API Stubs:** Generates `.pyi` stubs directly from your running Blender binary. Supports standard modules (`bpy`, `mathutils`) and runtime-generated properties (`bpy.context`, `bpy.types`).
* **Zero-Config Debugging:** Attach PyCharm’s debugger to Blender with a single click. No need for `pip install pydevd-pycharm` or remote server configuration.
* **Hot Reloading:** Updates your addon instantly when files are saved.
    * **Seamless Workflow:** Simply **switch focus back to Blender**, and your changes are applied automatically. (Requires PyCharm's "Save on frame deactivation" enabled).
    * **Keyboard Shortcut:** Also available via `Ctrl + Alt + Shift + R`.
* **Integrated Test Runner:** Run standard `unittest` suites inside Blender directly from PyCharm's UI.
* **Smart Code Insight:** Automatically suppresses false-positive warnings (PEP 8 naming for Operators/Panels, Property definitions).

#### ⚠️ Compatibility Note
* **Stable:** macOS
* **Experimental:** Windows & Linux support is currently experimental.
* Supported Blender Versions: 4.2+ and 5.x.

---
*Happy Blending! If you encounter any issues, please report them on GitHub.*

## [0.0.6] - 2026-01-13

### Added
- **CI/CD Integration**: The Project Wizard now generates a fully configured GitHub Actions workflow (`.github/workflows/ci.yml`).
    - **Zero Config**: Automatically sets up headless Blender (Linux) testing and code linting (Ruff) for new projects.
    - **Dependabot**: Includes `dependabot.yml` to keep actions and dependencies up to date.
- **Portable Test Runner**: A `run_tests.py` script is now generated in the `tests/` directory.
    - **Consistency**: Enables running tests in CI environments with the exact same logic used inside the IDE.
    - **Fallback Logic**: The Run Configuration now prioritizes the project's local runner script if it exists, falling back to the plugin's internal runner only if necessary.
- **Modern Packaging**: Projects now include a `pyproject.toml` file compliant with modern Python standards (compatible with `uv`/`hatchling`) and pre-configured for PyCharm integration.
- **Git Configuration**: Added generation of a comprehensive `.gitignore` file, specifically tuned for Blender addon development (ignoring `.blender_stubs`, `.venv`, `.vscode`, build artifacts, etc.).

### Fixed
- **Wizard UI Crash**: Fixed a critical `IllegalArgumentException` that occurred on specific IDE versions when initializing the "New Project" wizard settings panel.

## [0.0.5] - 2026-01-13

### Added
- **Zero-Config Debugging**: Introduced native debugging support. You can now launch Blender in Debug mode directly from PyCharm, automatically attaching the debugger to your addon code.
    - **No Setup Required**: The plugin automatically detects and injects PyCharm's bundled `pydevd` debugger. No `pip install` or remote server configuration is needed.
    - **Full Capability**: Supports breakpoints, variable inspection, and stepping through code within Blender.
- **Hot Reloading**: Added a "Reload Addon in Blender" action (`Ctrl+Alt+Shift+R`) to apply code changes instantly without restarting Blender.
    - **Deep Reload**: Implemented a smart module purging mechanism that ensures submodules are correctly re-imported and re-registered.
- **Connection Diagnostics**: Added a "Ping Blender Probe" action to the Tools menu to verify connectivity between PyCharm and the Blender instance.

### Improved
- **Communication Protocol**: Enhanced the internal socket protocol to fully support multi-byte characters (UTF-8), ensuring stability when reloading addons with non-ASCII names.
- **Resource Management**: Improved socket lifecycle management to prevent port leaks and ensure Blender processes are cleanly terminated if a debug session fails to start.
- **Path Resolution**: Enhanced the detection logic for `pydevd` to support a wider range of PyCharm distributions (Community, Professional, Ultimate) on macOS, Windows, and Linux.

## [0.0.4] - 2026-01-11

### Added
- **Project Wizard**: Introduced a "New Project" generator for creating Blender Addons compliant with Blender 4.2+ Extension standards.
    - **Test-Ready Scaffolding**: Automatically creates a production-ready directory structure with `blender_manifest.toml`, a separated Python package, and a GPLv3 license.
    - **Instant Testing**: Includes a pre-configured `tests/` directory and a sample test file, allowing developers to run tests immediately after project creation without complex setup.
- **Operator Autocompletion**: Expanded stub generation to include the `bpy.ops` module.
    - Users can now enjoy full code completion and documentation for all dynamic operators (e.g., `bpy.ops.mesh.primitive_cube_add`, `bpy.ops.object.select_all`).

## [0.0.3] - 2026-01-09

### Added
- **Code Inspection Suppression**: Implemented a custom suppressor to silence false positive warnings in PyCharm.
    - **Class Naming**: Suppresses PEP 8 naming warnings (N801) for valid Blender class names (e.g., `OBJECT_OT_my_operator`, `MYADDON_PT_panel`).
    - **Property Definitions**: Suppresses warnings for `bpy.props` types (e.g., `StringProperty`, `IntProperty`) and falls back gracefully when resolution fails.
- **Documentation Links**: Generated stubs now include direct URLs to the official Blender Python API documentation in their DocStrings for quick reference.

### Fixed
- **Stub Syntax Error**: Fixed a critical bug where certain methods in the generated stubs were missing parentheses `()`, resulting in invalid Python syntax.
- **Missing App Modules**: Fixed an issue where submodules under `bpy.app` (such as `handlers`, `timers`, `translations`, `icons`) were not being generated.

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