# Running & Debugging Your Addon

You can launch Blender with your addon loaded and attach the debugger directly.

## Setup

1.  Open **Run/Debug Configurations** (Top right dropdown > Edit Configurations).
2.  Click the **+** button and select **Blender**.
3.  **Name**: Give it a name (e.g., "BlenderRun").
4.  Click **OK**.

<p align="center">
  <img src="images/test1.png" width="48%" style="border: 1px solid #ddd; border-radius: 4px;">
  <img src="images/run_config.png" width="48%" style="border: 1px solid #ddd; border-radius: 4px;">
</p>

## Run or Debug

### Run

1.  Just Click the **Run** button. (The plugin auto-detects your addon root by the `blender_manifest.toml`.)

<div>
  <img src="images/run.png" style="border: 1px solid #ddd; border-radius: 4px;">
</div>

### Debug

1.  Set a breakpoint in your Python code (click the gutter next to a line number).
2.  Click the **Debug** (Bug icon) button.
3.  Blender will launch, and PyCharm will automatically attach. Execution will pause at your breakpoints.
4.  Force Viewport Redraw: While paused at a breakpoint, you can force Blender to redraw its UI/Viewport to see the current state. (⚠️ *Note: This can be a heavy operation depending on your scene. Avoid rapid repeated presses.*)
    * **Windows / Linux**: `Ctrl + Alt + Shift + D`
    * **macOS**: `Cmd + Opt + Shift + D`

<div>
  <img src="images/debug.png" style="border: 1px solid #ddd; border-radius: 4px;">
</div>

## Hot Reloading

When developing UI panels or iterating on operators, restarting Blender is slow. Use Hot Reload to apply changes instantly.

1.  Ensure Blender is running (launched via the **Run** or **Debug** configuration from PyCharm).
2.  Make changes to your Python code.
3.  Go to **Tools** > **Reload Addon in Blender**.
    * **Automatically**: Simply **switch focus** from PyCharm back to Blender. (Requires **Save files when switching to a different application** enabled in **System Settings > Autosave**).
    * **Manually**: Save your files manually according to your environment's setup.
4.  Check the Blender console or PyCharm notification for confirmation. Your addon is now running the updated code.

> **Note**: This performs a "Deep Reload" by unregistering the addon, purging relevant modules from `sys.modules`, and re-registering. This handles most code changes, but complex state changes may still require a restart.
