package com.github.iodine98.intellijdocumentationplugin.docintention

import com.intellij.codeInsight.intention.IntentionAction
import com.intellij.codeInsight.intention.PsiElementBaseIntentionAction
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiElement
import com.intellij.psi.util.elementType

class DocumentationIntention : PsiElementBaseIntentionAction(), IntentionAction {
    override fun startInWriteAction(): Boolean = true

    override fun getText(): String = "Write documentation for this method"

    override fun getFamilyName(): String = "Write documentation"

    override fun isAvailable(project: Project, editor: Editor?, element: PsiElement): Boolean {
        val elementType = element.parent.elementType
        return false
    }

    override fun invoke(project: Project, editor: Editor?, element: PsiElement) {
        TODO("Not yet implemented")
    }
}