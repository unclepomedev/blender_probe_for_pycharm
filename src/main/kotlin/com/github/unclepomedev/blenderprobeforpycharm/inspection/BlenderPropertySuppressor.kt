package com.github.unclepomedev.blenderprobeforpycharm.inspection

import com.intellij.codeInspection.InspectionSuppressor
import com.intellij.codeInspection.SuppressQuickFix
import com.intellij.psi.PsiElement
import com.intellij.psi.util.PsiTreeUtil
import com.jetbrains.python.psi.*

class BlenderPropertySuppressor : InspectionSuppressor {

    companion object {
        private val ALLOWED_IDS = setOf("PyTypeChecker", "PyAnnotation", "PyTypeHints", "PyPep8Naming")

        private val BLENDER_PROPS = setOf(
            "StringProperty", "IntProperty", "BoolProperty", "FloatProperty",
            "EnumProperty", "PointerProperty", "CollectionProperty",
            "FloatVectorProperty", "IntVectorProperty", "BoolVectorProperty",
            "RemoveProperty"
        )

        // Matches Blender naming convention (e.g. MY_ADDON_OT_op_name). Allows underscores in the prefix.
        private val BLENDER_NAMING_REGEX = Regex("^[A-Z][A-Z0-9_]+_[A-Z]{2}_[a-z0-9_]+$")
    }

    @Suppress("UnstableApiUsage")
    override fun isSuppressedFor(element: PsiElement, toolId: String): Boolean {
        if (toolId !in ALLOWED_IDS) {
            return false
        }

        if (toolId == "PyPep8Naming") {
            var targetClass = element as? PyClass

            if (targetClass == null) {
                val parent = element.parent
                if (parent is PyClass && parent.nameIdentifier == element) {
                    targetClass = parent
                }
            }

            if (targetClass != null) {
                val className = targetClass.name
                if (className != null && BLENDER_NAMING_REGEX.matches(className)) {
                    return true
                }
            }
            return false
        }

        val annotation = PsiTreeUtil.getParentOfType(element, PyAnnotation::class.java) ?: return false
        val value = annotation.value as? PyCallExpression ?: return false
        val callee = value.callee as? PyReferenceExpression ?: return false

        val resolved = callee.reference.resolve()

        if (resolved is PyQualifiedNameOwner) {
            val qName = resolved.qualifiedName
            if (qName != null) {
                return qName.startsWith("bpy.props.")
            }
        }
        val text = callee.text ?: return false
        val name = callee.referencedName ?: return false

        // Accept strict "bpy.props.*" prefix OR simple name match as a fallback.
        // Fallback is needed for cases like "from bpy.props import StringProperty" where resolution fails.
        // Note: This matches any function with these names, creating a potential false positive trade-off.
        return text.startsWith("bpy.props.") || name in BLENDER_PROPS
    }

    override fun getSuppressActions(element: PsiElement?, toolId: String): Array<SuppressQuickFix> {
        return SuppressQuickFix.EMPTY_ARRAY
    }
}