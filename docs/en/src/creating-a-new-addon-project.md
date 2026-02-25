# Creating a New Addon Project

You can start development with a simple addon, along with a structure that includes tests and CI already set up.

1. Go to **File** > **New Project**
2. Select **Blender addon** from the generator list on the left.
3. Configure your project location and click **Create**.

<div>
  <img src="images/wizard.png" alt="New Project wizard with Blender addon generator" style="width: 100%; border: 1px solid #ddd; border-radius: 4px;">
</div>

This generates a clean project structure compliant with Blender 4.2+ Extensions:

* `my_addon_package/`: The addon's Python package. A package with the name you entered in the wizard will be created.
* `tests/`: A ready-to-run test suite.
* `.github/`: CI workflows for GitHub Actions.
* `LICENSE`: A GPLv3 license file.
* `pyproject.toml`: Python tooling configuration.