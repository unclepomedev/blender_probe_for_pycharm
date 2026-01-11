# Blender Probe
<!-- Plugin description -->
**Blender Probe** is a PyCharm plugin designed to streamline Blender Python API (`bpy`) development. It bridges the gap between PyCharm and Blender, providing robust code completion and a fully integrated test runner.

> **⚠️ Compatibility Note:**
> While **Blender Probe** is designed to work across Windows, macOS, and Linux, primary development and extensive testing have been conducted on **macOS**.
> Windows and Linux support is currently **experimental**. If you encounter any pathing issues or unexpected behavior on these platforms, please [open an issue](https://github.com/unclepomedev/blender_probe_for_pycharm/issues).

## Features

* **Dynamic API Stubs**: Generates Python type stubs (`.pyi`) via runtime introspection and automatically registers them as a Source Root.
  * Unlike static packages, this guarantees your stubs match your exact Blender binary—including daily builds and custom branches.
  * **Documentation Integration**: Generated stubs include direct links to the official Blender Python API reference within the IDE's Quick Documentation, allowing for instant lookups.
* **Code Insight**: Automatically suppresses common false-positive warnings in PyCharm to match Blender's conventions.
  * **PEP 8 Compliance**: Ignores N801 naming warnings for valid Blender classes (e.g., `OBJECT_OT_my_operator`, `MY_PT_panel`).
  * **Property Handling**: Correctly handles `bpy.props` definitions without triggering type-checking errors.
* **Integrated Test Runner**: Run standard Python `unittest` suites inside Blender directly from PyCharm.
  * **Visual Feedback**: View results in PyCharm's native test runner UI with green/red bars and tree navigation.
  * **Clean Environment**: Tests run with `--factory-startup` to ensure a reproducible environment free from user preferences or third-party addons.
  * **Automatic Path Injection**: Your project root is automatically injected into `sys.path`, allowing you to import your addon modules directly in tests without manual configuration.
* **Project Wizard (Test-Ready)**: Instantly scaffolds a clean, minimal project structure compliant with Blender 4.2+ Extensions.
  * **Modern Structure**: Generates a standard directory layout with `blender_manifest.toml`, a separated Python package, and a GPLv3 license.
  * **Test-Driven Ready**: Comes with a pre-configured `tests/` folder and a sample test. You can run your first test immediately after project creation—no complex environment setup required.

## Prerequisites
<!-- Plugin description end -->
* **PyCharm** (Community or Professional) 2025.2+
* **Blender** 4.2+ or 5.x
  * *Note: Blender versions 4.1 and older are not supported.*

## Configuration

Before using the plugin, you must configure the path to your Blender executable.

1.  Go to **Settings/Preferences** > **Tools** > **Blender Probe**.
2.  Set the **Blender Executable Path**:
  * **Windows**: `C:\Program Files\Blender Foundation\Blender 5.0\blender.exe`
  * **macOS**: `/Applications/Blender.app/Contents/MacOS/Blender`
  * **Linux**: `/usr/bin/blender`
3.  Click **OK**.

## Usage

### 1. Creating a New Addon Project

Start your development with a production-ready structure.

1. Go to **File** > **New Project**
2. Select **Blender Addon** from the generator list on the left.
3. Configure your project location and click **Create**.

This generates a clean project structure compliant with Blender 4.2+ Extensions:

* `my_addon_package/`: Your actual Python package (source code).
  * Contains `blender_manifest.toml`, `__init__.py`, `operators.py`, and `panel.py`.
* `tests/`: A ready-to-run test suite.
* `LICENSE`: A GPLv3 license file (standard for Blender addons).

### 2. Generating Code Stubs (Autocompletion)

To enable code completion for `bpy` modules:

1.  Open your project in PyCharm.
2.  Go to **Tools** > **Regenerate Blender Stubs**.
  * *Alternatively, use "Find Action" (Cmd/Ctrl+Shift+A) and search for "Regenerate Blender Stubs".*
3.  Wait for the progress bar to finish. A hidden folder `.blender_stubs` will be created in your project root and automatically marked as a Source Root.

> **💡 Tip:** The `.blender_stubs` folder contains generated files that do not need to be version controlled. It is highly recommended to add `.blender_stubs/` to your project's `.gitignore` file.

### 3. Running Tests

You can run `unittest` scripts inside Blender without leaving PyCharm.

1.  Create a standard Python test file (e.g., `tests/test_sample.py`).
2.  Open **Run/Debug Configurations** (Top right dropdown > Edit Configurations).
3.  Click the **+** button and select **Blender Test**.
4.  **Name**: Give it a name (e.g., "All Tests").
5.  **Test Directory**: Select the folder containing your test scripts.
6.  Click **Run** (Green Play Button).

#### Writing Tests for Blender Probe

Since the runner uses `--factory-startup` for a clean state, the default scene (Cube, Camera, Light) will be present. It is recommended to clean the scene in your `setUp` method.

> **Note:** The plugin automatically adds your project root to `sys.path`. You can import your local addon modules directly (e.g., `from my_addon import logic`) without manual path setup.

**Example `tests/test_sample.py`:**

```python
import unittest
import bpy
# from my_addon import logic  <-- Works automatically!

class MyBlenderTest(unittest.TestCase):
    
    def setUp(self):
        # Clear the default scene (Cube, Camera, Light) before each test
        bpy.ops.wm.read_homefile(use_empty=True)

    def test_create_cube(self):
        # Verify the scene is empty
        self.assertEqual(len(bpy.data.objects), 0)
        
        # Create a new object
        bpy.ops.mesh.primitive_cube_add()
        
        # Verify creation
        self.assertEqual(len(bpy.data.objects), 1)
        self.assertEqual(bpy.context.object.name, "Cube")
```

## License

Licensed under the MIT License.
