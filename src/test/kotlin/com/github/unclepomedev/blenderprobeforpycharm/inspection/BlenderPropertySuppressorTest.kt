package com.github.unclepomedev.blenderprobeforpycharm.inspection

import com.intellij.psi.util.PsiTreeUtil
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import com.jetbrains.python.PythonFileType
import com.jetbrains.python.psi.PyAnnotation
import com.jetbrains.python.psi.PyClass
import com.jetbrains.python.psi.PyFile

class BlenderPropertySuppressorTest : BasePlatformTestCase() {

    private val suppressor = BlenderPropertySuppressor()

    fun testBlenderNamingConvention() {
        val psiFile = myFixture.configureByText(
            PythonFileType.INSTANCE, """
            import bpy

            class MY_ADDON_OT_test_operator(bpy.types.Operator):
                bl_idname = "my.operator"
                
            class NORMAL_Class(bpy.types.Operator):
                pass
                
            class StandardClassName:
                pass
        """.trimIndent()
        ) as PyFile

        val classes = PsiTreeUtil.findChildrenOfType(psiFile, PyClass::class.java).toList()

        val blenderClass = classes.find { it.name == "MY_ADDON_OT_test_operator" }
        assertNotNull(blenderClass)
        assertTrue(
            "Blender naming convention should be suppressed for PyPep8Naming",
            suppressor.isSuppressedFor(blenderClass!!.nameIdentifier!!, "PyPep8Naming")
        )

        val standardClass = classes.find { it.name == "StandardClassName" }
        assertNotNull(standardClass)
        assertFalse(
            "Standard naming should not trigger suppression logic (default logic applies)",
            suppressor.isSuppressedFor(standardClass!!.nameIdentifier!!, "PyPep8Naming")
        )
    }

    fun testPropertySuppression() {
        val psiFile = myFixture.configureByText(
            PythonFileType.INSTANCE, """
            import bpy
            from bpy.props import BoolProperty

            class MyPanel(bpy.types.Panel):
                prop_a: bpy.props.StringProperty()
                
                prop_b: BoolProperty()
                
                prop_c: int = 10
        """.trimIndent()
        ) as PyFile

        val annotations = PsiTreeUtil.findChildrenOfType(psiFile, PyAnnotation::class.java).toList()

        val propA = annotations.find { it.text.contains("StringProperty") }
        assertNotNull(propA)

        val elementInPropA =
            PsiTreeUtil.findChildrenOfType(propA, com.jetbrains.python.psi.PyReferenceExpression::class.java).last()

        assertTrue(
            "bpy.props full qualification should be suppressed",
            suppressor.isSuppressedFor(elementInPropA, "PyTypeChecker")
        )

        val propB = annotations.find { it.text.contains("BoolProperty") }
        assertNotNull(propB)
        val elementInPropB =
            PsiTreeUtil.findChildrenOfType(propB, com.jetbrains.python.psi.PyReferenceExpression::class.java).last()

        assertTrue(
            "Imported property name should be suppressed",
            suppressor.isSuppressedFor(elementInPropB, "PyTypeChecker")
        )

        val propC = annotations.find { it.text.contains("int") }
        assertNotNull(propC)
        val elementInPropC =
            PsiTreeUtil.findChildrenOfType(propC, com.jetbrains.python.psi.PyReferenceExpression::class.java).last()

        assertFalse(
            "Standard types should not be suppressed",
            suppressor.isSuppressedFor(elementInPropC, "PyTypeChecker")
        )
    }

    fun testUnknownToolId() {
        val psiFile = myFixture.configureByText(PythonFileType.INSTANCE, "class A: pass") as PyFile
        val pyClass = PsiTreeUtil.findChildOfType(psiFile, PyClass::class.java)

        assertFalse(
            "Should return false for unrelated tool IDs",
            suppressor.isSuppressedFor(pyClass!!, "SomeRandomCheck")
        )
    }
}