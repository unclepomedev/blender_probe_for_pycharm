# Generating Code Stubs (Autocompletion)

To enable code completion for `bpy` and other Blender modules:

1.  Open your project in PyCharm.
2.  Go to **Tools** > **Regenerate Blender Stubs**.
3.  Wait for the progress bar to finish. A hidden folder `.blender_stubs` will be created in your project root and automatically marked as a Source Root.

<div>
  <img src="images/generate_stubs.png" alt="PyCharm menu showing Regenerate Blender Stubs action" style="border: 1px solid #ddd; border-radius: 4px;">
</div>

> **💡 Tip:** The `.blender_stubs` folder contains generated files that do not need to be version controlled. It is recommended to add `.blender_stubs/` to your project's `.gitignore` file.
> *(If you created your project using the **Blender Addon** wizard, this is already configured.)*

> **💡 Tip:** Since Blender's API is highly dynamic, PyCharm sometimes cannot infer types automatically. To get full autocompletion, use **Type Hinting**:
> ```python
> def my_func(context: bpy.types.Context):
>     obj: bpy.types.Object = context.active_object
>     print(obj.location) # Autocompletion works
> ```

> **💡 Tip:** If the generated stubs don't meet your needs, you can delete `.blender_stubs` and use static stubs like `fake-bpy-module`.
> **💡 Tip:** In teams that require reproducible type checking (mypy/pyright/pyrefly/ty) across environments or Blender versions, committing `.blender_stubs` can be a valid exception. If you do, configure `.gitattributes` accordingly.
