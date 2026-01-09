package com.github.unclepomedev.blenderprobeforpycharm.inspection

import com.intellij.codeInspection.InspectionSuppressor
import com.intellij.codeInspection.SuppressQuickFix
import com.intellij.psi.PsiElement
import com.intellij.psi.util.PsiTreeUtil
import com.jetbrains.python.psi.*

class BlenderPropertySuppressor : InspectionSuppressor {

    private val blenderProps = setOf(
        "StringProperty", "IntProperty", "BoolProperty", "FloatProperty",
        "EnumProperty", "PointerProperty", "CollectionProperty",
        "FloatVectorProperty", "IntVectorProperty", "BoolVectorProperty",
        "RemoveProperty"
    )

    private val blenderNamingRegex = Regex("^[A-Z][A-Z0-9_]+_[A-Z]{2}_[a-z0-9_]+$")

    @Suppress("UnstableApiUsage")
    override fun isSuppressedFor(element: PsiElement, toolId: String): Boolean {
        val allowedIds = setOf("PyTypeChecker", "PyAnnotation", "PyTypeHints", "PyPep8Naming")

        if (toolId !in allowedIds) {
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
                if (className != null && blenderNamingRegex.matches(className)) {
                    return true
                }
            }
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