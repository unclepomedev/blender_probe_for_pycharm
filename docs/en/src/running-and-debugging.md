# Running & Debugging Your Addon

You can launch Blender with your addon loaded, or use the debugger.

## Setup

1.  Open **Run/Debug Configurations** (Top right dropdown > Edit Configurations).
2.  Click the **+** button and select **Blender**.
3.  **Name**: Give it a name (e.g., "BlenderRun").
4.  Click **OK**.

<p align="center">
  <img src="images/test1.png" alt="Lead to Run or Debug Configuration" width="48%" style="border: 1px solid #ddd; border-radius: 4px;">
  <img src="images/run_config.png" alt="Run or Debug Configuration" width="48%" style="border: 1px solid #ddd; border-radius: 4px;">
</p>

## Run or Debug

### Run

1.  Just Click the **Run** button. (The plugin auto-detects your addon root by the `blender_manifest.toml`.)

<div>
  <img src="images/run.png" alt="Run the addon" style="border: 1px solid #ddd; border-radius: 4px;">
</div>

### Debug

1.  Set a breakpoint in your Python code (click the gutter next to a line number).
2.  Click the **Debug** (Bug icon) button.
3.  Blender will launch, and PyCharm will automatically attach. When a breakpoint is reached, execution will pause.
4.  Force Viewport Redraw: While paused at a breakpoint, you can force Blender to redraw its UI/Viewport to see the current state. (⚠️ *Note: This can be a heavy operation depending on your scene. Avoid rapid repeated presses.*)
    * **Windows / Linux**: `Ctrl + Alt + Shift + D`
    * **macOS**: `Cmd + Opt + Shift + D`

<div>
  <img src="images/debug.png" alt="Debug the addon" style="border: 1px solid #ddd; border-radius: 4px;">
</div>

## Hot Reloading

You can use Hot Reload to apply changes instantly, making it quick to verify code changes for UI panels, operators, and more.

1.  Suppose Blender is running, launched via the **Run** or **Debug** configuration from PyCharm.
2.  Make changes to your Python code.
3.  The reload is triggered by one of the following methods:
    * **Automatically**: Simply **switch focus** from PyCharm back to Blender. (Requires **Save files when switching to a different application** enabled in **System Settings > Autosave**).
    * **Manually**: Save your files manually according to your environment's setup.
4.  Check the Blender console or PyCharm notification for confirmation. Your addon is now running the updated code.

> **Note**: This performs a "Deep Reload" by unregistering the addon, purging relevant modules from `sys.modules`, and re-registering. This handles most code changes, but complex state changes may still require a restart.
