import inspect
import bpy
from .context import StubContext


class StubAnalyzer:
    """
    Analyzes Blender internal data structures to extract relationships.
    """
    def __init__(self, context: StubContext):
        """
        Initializes the analyzer with the generation context.

        :param context: The shared stub context.
        """
        self.context = context

    def analyze_collections(self):
        """
        Analyzes bpy.types to determine the element type of collection properties.
        Populates context.collection_mapping with the results.
        """
        print("Analyzing collection relationships...")
        for name in dir(bpy.types):
            cls = getattr(bpy.types, name)
            if not inspect.isclass(cls) or not hasattr(cls, "bl_rna"):
                continue

            for prop in cls.bl_rna.properties:
                if prop.type == "COLLECTION":
                    if hasattr(prop, "srna") and prop.srna and prop.fixed_type:
                        container_id = prop.srna.identifier
                        element_id = getattr(prop.fixed_type, "identifier", None)
                        if (
                            container_id
                            and element_id
                            and hasattr(bpy.types, container_id)
                        ):
                            self.context.collection_mapping[container_id] = element_id
