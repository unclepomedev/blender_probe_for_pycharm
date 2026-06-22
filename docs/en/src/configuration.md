# Configuration

Blender Probe requires the Blender runtime to function; therefore, you must configure the runtime settings before using any of its features.

## OptionA: Automatic via `blup` 🦀

If you manage your Blender versions with [blup](https://github.com/unclepomedev/blup), no additional configuration is required. The plugin automatically detects the correct Blender executable based on your project's `.blender-version` file or the global default.

(This makes it easy to frequently switch the target Blender version for testing, or to track API changes by diffing generated type stubs across versions. The same applies to daily builds, enabling early response to upcoming changes.)

## OptionB: Manual Path Configuration

If you don't use `blup`, you must configure the path to your Blender executable before using the plugin.

1.  Go to **Settings/Preferences** > **Tools** > **Blender Probe**.
2.  Set the **Blender Executable Path**:
    * **Windows**: `C:\Program Files\Blender Foundation\Blender 5.0\blender.exe`
    * **macOS**: `/Applications/Blender.app/Contents/MacOS/Blender`
    * **Linux**: `/usr/bin/blender`
3.  Click **OK**.

<div>
  <img src="images/configuration.png" alt="Blender Executable Path configuration" style="width: 100%; border: 1px solid #ddd; border-radius: 4px;">
</div>

## Legacy Add-on Support

Blender Probe assumes the add-on uses the modern `blender_manifest.toml` format. If you work with an older add-on that does not, the following settings (under **Settings/Preferences** > **Tools** > **Blender Probe**) help:

* **Add-on name (fallback)**: When no `blender_manifest.toml` is found, the plugin normally derives the add-on module name from the project name. If your add-on's module name differs from the project folder, set the correct Python module name here. It is ignored when a manifest is present.
* **Launch Blender with `--factory-startup`**: Enabled by default to match the standard behavior. Disable it if your add-on relies on dependencies installed directly into your Blender user environment — `--factory-startup` prevents Blender from loading those user-space modules. This applies to both running/debugging and running tests.