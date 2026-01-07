import unittest
import bpy

class MyBlenderTest(unittest.TestCase):
    def setUp(self):
            bpy.ops.wm.read_homefile(use_empty=True)

        def test_object_creation(self):
            bpy.ops.mesh.primitive_cube_add()

            self.assertEqual(len(bpy.data.objects), 1)
            print("Cube created successfully!")

        def test_success_example(self):
            self.assertTrue(True, "This should pass")
