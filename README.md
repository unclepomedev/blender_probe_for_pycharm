# Blender Probe
<!-- Plugin description -->
**Blender Probe** bridges PyCharm and Blender with **Code Completion**, **Testing**, **Debugging**, and **Hot Reloading**.

> **⚠️ Compatibility Note: (Public Beta)**
> While **Blender Probe** is designed to work across Windows, macOS, and Linux, primary development and extensive testing have been conducted on **macOS**.
> Windows and Linux support is currently **experimental**. If you encounter any pathing issues or unexpected behavior on these platforms, please [open an issue](https://github.com/unclepomedev/blender_probe_for_pycharm/issues).

## Features

* **Dynamic API Stubs**: Generates Python type `.pyi` stubs (including daily builds) via runtime introspection.
* **No-Config Debugging**: Attach PyCharm’s native debugger to Blender with a single click.
* **Hot Reloading**: Instantly reload your addon code **on file save** without restarting.
* **Code Insight**: Intelligent suppression of false-positive warnings.
* **Integrated Test Runner**: Run standard `unittest` suites inside Blender from PyCharm's UI.
* **Project Wizard**: Scaffolds a Blender 4.2+ Extensions compliant project.

Detailed description: [GitHub Repository](https://github.com/unclepomedev/blender_probe_for_pycharm)
<!-- Plugin description end -->

## Prerequisites
* **PyCharm** (Community or Professional) 2025.2+
* **Blender** 4.2+ or 5.x
    * *Note: Blender versions 4.1 and older are not supported.*

## Configuration

### OptionA: Automatic via `blup` 🦀

If you manage your Blender versions with [blup](https://github.com/unclepomedev/blup), no additional configuration is required. The plugin automatically detects the correct Blender executable based on your project's .blender-version file or the global default.

### OptionB: Manual Path Configuration

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
* `tests/`: A ready-to-run test suite.
* `.github/`: CI/CD workflows for GitHub Actions.
* `LICENSE`: A GPLv3 license file.
* `pyproject.toml`: Python tooling configuration.

### 2. Generating Code Stubs (Autocompletion)

To enable code completion for `bpy` modules:

1.  Open your project in PyCharm.
2.  Go to **Tools** > **Regenerate Blender Stubs**.
3.  Wait for the progress bar to finish. A hidden folder `.blender_stubs` will be created in your project root and automatically marked as a Source Root.

> **💡 Tip:** The `.blender_stubs` folder contains generated files that do not need to be version controlled. It is highly recommended to add `.blender_stubs/` to your project's `.gitignore` file.
> *(Note: If you created your project using the **Blender Addon** wizard, this is already configured for you.)*

> **💡 Tip:** Since Blender's API is highly dynamic, PyCharm sometimes cannot infer types automatically (especially for `bpy.context`). To get full autocompletion, use **Type Hinting**:
> ```python
> def my_func(context: bpy.types.Context):
>     obj: bpy.types.Object = context.active_object
>     print(obj.location) # Autocompletion works
> ```

### 3. Running & Debugging Your Addon

You can launch Blender with your addon loaded and attach the debugger directly.

1.  Open **Run/Debug Configurations** (Top right dropdown > Edit Configurations).
2.  Click the **+** button and select **Blender**.
3.  **Name**: Give it a name (e.g., "Run Blender").
4.  **Script Path**: Select your addon's entry point (e.g., `my_addon_package/__init__.py`) or leave empty to just open Blender with the project path injected.
5.  Click **Apply**.

**To Run:** Click the green **Run** (Play) button. Blender will open with your addon code available.

**To Debug:**
1.  Set a breakpoint in your Python code (click the gutter next to a line number).
2.  Click the **Debug** (Bug icon) button.
3.  Blender will launch, and PyCharm will automatically attach. Execution will pause at your breakpoints.

### 4. Hot Reloading

When developing UI panels or iterating on operators, restarting Blender is slow. Use Hot Reload to apply changes instantly.

1.  Ensure Blender is running (launched via the **Run** or **Debug** configuration from PyCharm).
2.  Make changes to your Python code.
3.  Go to **Tools** > **Reload Addon in Blender**.
    * **Automatically**: Simply **switch focus** from PyCharm back to Blender. (Requires **Save files when switching to a different application** enabled in **System Settings > Autosave**).
    * **Manually**: `Ctrl + Alt + Shift + R` (default) or goto **Tools > Reload Addon in Blender**.
4.  Check the Blender console or PyCharm notification for confirmation. Your addon is now running the updated code.

> **Note**: This performs a "Deep Reload" by unregistering the addon, purging relevant modules from `sys.modules`, and re-registering. This handles most code changes, but complex state changes may still require a restart.

### 5. Running Tests

You can run `unittest` scripts inside Blender without leaving PyCharm.

1.  Open **Run/Debug Configurations**.
2.  Click the **+** button and select **Blender Test**.
3.  **Name**: Give it a name (e.g., "All Tests").
4.  **Test Directory**: Select the folder containing your test scripts.
5.  Click **Run**.

#### Writing Tests for Blender Probe

> **🚀 Fast Track:** If you used the **Project Wizard**, a fully configured test file (`tests/test_sample.py`) is already included.

For existing projects or manual setup, follow the structure below.

Since the runner uses `--factory-startup` for a clean state, the default scene (Cube, Camera, Light) will be present. It is recommended to clean the scene in your `setUp` method.

> **Note:** The plugin automatically adds your project root to `sys.path`. You can import your local addon modules directly (e.g., `from my_addon_package import operators`) without manual path setup.

**Example `tests/test_sample.py`:**

```python
import unittest
import bpy
# from my_addon_package import operators  <-- Works automatically!

class TestSampleOperator(unittest.TestCase):
  """
  Integration tests for a custom operator.
  Runs inside Blender via Blender Probe.
  """

  def setUp(self):
    # 1. Reset Blender to a clean state
    bpy.ops.wm.read_homefile(use_empty=True)

    # 2. Setup a test scene (Create a Cube)
    bpy.ops.mesh.primitive_cube_add()
    self.test_obj = bpy.context.object
    self.test_obj.name = "TestCube"

  def test_operator_logic(self):
    """Verify that the operator renames the object and adds a property"""

    # [Arrange] Ensure initial state
    self.assertEqual(self.test_obj.name, "TestCube")

    # [Act]
    # Execute your custom operator (e.g., defined in your addon)
    # Context is automatically handled by Blender.
    result = bpy.ops.object.sample_operator()

    # [Assert]
    # 1. Check return value
    self.assertIn('FINISHED', result)

    # 2. Check side effects (Logic verification)
    self.assertEqual(self.test_obj.name, "TestCube_processed")
    self.assertTrue(self.test_obj.get("is_processed"))
```

### 6. Continuous Integration (CI)

Projects created with the **Blender Addon** wizard come with a pre-configured GitHub Actions workflow (`.github/workflows/ci.yml`).

* **Zero Config:** Just push your code to GitHub.
* **Automatic Testing:** The workflow automatically installs a headless version of Blender (Linux) and runs your tests using the same runner logic as the IDE.
* **Linting:** `uv` based linting (Ruff) checks your code style.
* **Dependabot:** Keeps your actions and dependencies up to date.

## License

Licensed under the MIT License.
