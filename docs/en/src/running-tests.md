# Tests

> **🚀 Fast Track:** If you used the **Project Wizard**, fully configured test files (`tests/run_tests.py` and `tests/test_sample.py`) are already included.
>
> 💡 Tip: Setting up tests that run on a real Blender instance while integrating natively with PyCharm (TeamCity) and ensuring the exact same configuration runs in CI is inherently complex. To set up tests in an existing project, the quickest way is to generate the test files using the Project Wizard in a temporary project and then copy-paste them into your own.

## PyCharm native unit testing

You can use PyCharm `unittest`.

1.  Open **Run/Debug Configurations**.
2.  Click the **+** button and select **Blender Test**.
3.  **Name**: Give it a name (e.g., "All Tests").
4.  **Test Directory**: Select the folder containing your test scripts.
5.  Create a `run_tests.py` file like [this template](https://github.com/unclepomedev/blender_probe_for_pycharm/blob/0903621d8cd9ea3602a8911d8eda1681b1782361/src/main/resources/fileTemplates/internal/BlenderAddon_RunTests.py.ft) in your test folder.
6.  Click the **Run** button.

<div style="display: flex; align-items: center; justify-content: center; gap: 10px; margin: 20px 0;">
  <div style="width: 40%; display: flex; flex-direction: column; gap: 10px;">
    <img src="images/test1.png" alt="Lead to test configuration" style="width: 100%; border: 1px solid #ddd; border-radius: 4px;">
    <img src="images/test3.png" alt="Execute the tests" style="width: 100%; border: 1px solid #ddd; border-radius: 4px;">
  </div>
  <div style="width: 60%;">
    <img src="images/test2.png" alt="Test configuration" style="width: 100%; border: 1px solid #ddd; border-radius: 4px;">
  </div>
</div>

### Writing Tests

Once configured, you can implement tests as below and begin practicing TDD.

```python
# test_sample.py
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

        # [Arrange]
        # Ensure initial state
        self.assertEqual(self.test_obj.name, "TestCube")
        self.assertNotIn("is_processed", self.test_obj)

        # [Act]
        # Execute your custom operator (e.g., defined in your addon)
        # Context is automatically handled by Blender.
        result = bpy.ops.object.sample_operator()

        # [Assert]
        # 1. Check return value
        self.assertIn("FINISHED", result)

        # 2. Check side effects (Logic verification)
        self.assertEqual(self.test_obj.name, "TestCube_processed")
        self.assertTrue(self.test_obj.get("is_processed"))


if __name__ == "__main__":
    unittest.main()
```

## CI

Projects created with the **Blender addon** wizard come with a pre-configured GitHub Actions workflow (`.github/workflows/ci.yml`).

* **No Config:** Just push your code to GitHub.
* **Automatic Testing:** The workflow automatically installs a headless version of Blender (Linux) and runs your tests using the same runner logic as the IDE.
* **Linting:** Ruff checks your code style.
* **Dependabot:** Keeps your actions and dependencies up to date.
