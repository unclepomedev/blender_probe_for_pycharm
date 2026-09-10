# Configuration

Blender Probe requires the Blender runtime to function; therefore, you must configure the runtime settings before using any of its features.

## OptionA: Automatic via `blup` 🦀

If you manage your Blender versions with [blup](https://github.com/unclepomedev/blup), no additional configuration is required. The plugin automatically detects the correct Blender executable based on your project's `.blender-version` file or the global default.

(This makes it easy to frequently switch the target Blender version for testing, or to track API changes by diffing generated type stubs across versions. The same applies to daily builds, enabling early response to upcoming changes.)

## OptionB: Manual Executable Registration & Switching

If you don't use `blup`, or if you want to quickly switch between multiple Blender versions (such as stable, LTS, and daily builds), you can register and manage Blender binaries in the settings dialog (similar to managing Python interpreters in PyCharm).

1. Go to **Settings/Preferences** > **Tools** > **Blender Probe**.
2. Click the **+** button under the **Configured Blender Executables** table, specify a **Name** (e.g. `Blender 5.2 LTS`), and select the **Executable path**:
   * **Windows**: `C:\Program Files\Blender Foundation\Blender 5.2\blender.exe`
   * **macOS**: `/Applications/Blender.app/Contents/MacOS/Blender`
   * **Linux**: `/usr/bin/blender`
3. Select the binary you want to use from the **Active Blender executable** dropdown (choose `<Auto-detect via blup>` to use `blup` automatic detection).
4. Click **OK** or **Apply**.

<div>
  <img src="images/configuration.png" alt="Configured Blender Executables table and active executable selector" style="width: 100%; border: 1px solid #ddd; border-radius: 4px;">
</div>

## Legacy Add-on Support

* **Launch Blender with `--factory-startup`** (under **Settings/Preferences** > **Tools** > **Blender Probe**):
  Enabled by default to match the standard, supported behavior. It skips loading the user's `startup.blend` file across application runs, test runs, and stub generation, ensuring a clean and reproducible environment. Disable it only if your workflow specifically requires loading your custom default startup file.

  > **Warning:** Disabling `--factory-startup` loads your customized startup file, which may alter the initial scene or state and lead to unexpected behavior during testing or running.

## Add-on Manifest Location

By default, Blender Probe finds your add-on by searching the project for a `blender_manifest.toml`. Directories such as virtual environments, build output and `tests` are skipped. When more than one manifest remains, the shallowest path is chosen, and the plugin tells you which one it picked.

If that isn't the one you want — or if a dependency in your project ships its own manifest — set **Manifest file override** (**Settings/Preferences** > **Tools** > **Blender Probe**) to the manifest of your add-on. Leave it empty to keep the automatic search.