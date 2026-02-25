# Creating a New Addon Project

Start your development with a production-ready structure.

1. Go to **File** > **New Project**
2. Select **Blender addon** from the generator list on the left.
3. Configure your project location and click **Create**.

<div>
  <img src="images/wizard.png" alt="New Project wizard with Blender addon generator" style="width: 100%; border: 1px solid #ddd; border-radius: 4px;">
</div>

This generates a clean project structure compliant with Blender 4.2+ Extensions:

* `my_addon_package/`: Your actual Python package (source code).
* `tests/`: A ready-to-run test suite.
* `.github/`: CI workflows for GitHub Actions.
* `LICENSE`: A GPLv3 license file.
* `pyproject.toml`: Python tooling configuration.