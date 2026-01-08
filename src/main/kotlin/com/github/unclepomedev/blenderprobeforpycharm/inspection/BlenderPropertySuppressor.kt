package com.github.unclepomedev.blenderprobeforpycharm.inspection

import com.intellij.codeInspection.InspectionSuppressor
import com.intellij.codeInspection.SuppressQuickFix
import com.intellij.psi.PsiElement
import com.intellij.psi.util.PsiTreeUtil
import com.jetbrains.python.psi.PyAnnotation
import com.jetbrains.python.psi.PyCallExpression
import com.jetbrains.python.psi.PyQualifiedNameOwner
import com.jetbrains.python.psi.PyReferenceExpression

class BlenderPropertySuppressor : InspectionSuppressor {

    private val blenderProps = setOf(
        "StringProperty", "IntProperty", "BoolProperty", "FloatProperty",
        "EnumProperty", "PointerProperty", "CollectionProperty",
        "FloatVectorProperty", "IntVectorProperty", "BoolVectorProperty",
        "RemoveProperty"
    )

    override fun isSuppressedFor(element: PsiElement, toolId: String): Boolean {
        val allowedIds = setOf("PyTypeChecker", "PyAnnotation", "PyTypeHints")
        if (toolId !in allowedIds) {
            return false
        }

        val annotation = PsiTreeUtil.getParentOfType(element, PyAnnotation::class.java) ?: return false
        val value = annotation.value as? PyCallExpression ?: return false
        val callee = value.callee as? PyReferenceExpression ?: return false

        val resolved = callee.reference.resolve()

        if (resolved != null) {
            if (resolved is PyQualifiedNameOwner) {
                val qName = resolved.qualifiedName
                return qName != null && qName.startsWith("bpy.props")
            }
            return false
        }
        val text = callee.text ?: return false
        @Suppress("UnstableApiUsage")
        val name = callee.referencedName ?: return false

        return text.startsWith("bpy.props.") || blenderProps.contains(name)
    }

    override fun getSuppressActions(element: PsiElement?, toolId: String): Array<SuppressQuickFix> {
        return SuppressQuickFix.EMPTY_ARRAY
    }
}