# Configuration

Blender Probe requires the Blender runtime to function; therefore, you must configure the runtime settings before using any of its features.

## OptionA: Automatic via `blup` 🦀

If you manage your Blender versions with [blup](https://github.com/unclepomedev/blup), no additional configuration is required. The plugin automatically detects the correct Blender executable based on your project's `.blender-version` file or the global default.

## OptionB: Manual Path Configuration

Before using the plugin, you must configure the path to your Blender executable.

1.  Go to **Settings/Preferences** > **Tools** > **Blender Probe**.
2.  Set the **Blender Executable Path**:
    * **Windows**: `C:\Program Files\Blender Foundation\Blender 5.0\blender.exe`
    * **macOS**: `/Applications/Blender.app/Contents/MacOS/Blender`
    * **Linux**: `/usr/bin/blender`
3.  Click **OK**.

<div>
  <img src="images/configuration.png" alt="Blender Executable Path configuration" style="width: 100%; border: 1px solid #ddd; border-radius: 4px;">
</div>