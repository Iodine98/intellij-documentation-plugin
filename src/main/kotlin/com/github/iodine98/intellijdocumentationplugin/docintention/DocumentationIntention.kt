package com.github.iodine98.intellijdocumentationplugin.docintention

import com.aallam.openai.api.completion.CompletionRequest
import com.aallam.openai.api.completion.TextCompletion
import com.aallam.openai.api.model.ModelId
import com.aallam.openai.client.OpenAI
import com.intellij.codeInsight.intention.IntentionAction
import com.intellij.codeInsight.intention.PsiElementBaseIntentionAction
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiMethod
import com.intellij.refactoring.suggested.startOffset
import io.github.cdimascio.dotenv.dotenv
import kotlinx.coroutines.flow.Flow
import org.jetbrains.kotlin.psi.KtNamedFunction


open class DocumentationIntention : PsiElementBaseIntentionAction(), IntentionAction {
    override fun startInWriteAction(): Boolean = true

    override fun getText(): String = "Write documentation for this method"

    override fun getFamilyName(): String = "Write documentation"

    private fun PsiElement.checkFunctionType(languageDisplayName: String): Boolean {
        return when (languageDisplayName) {
            "Java" -> this is PsiMethod
            "Kotlin" -> this is KtNamedFunction
            else -> true
        }
    }

    private fun PsiElement.getNearestFunction(languageDisplayName: String): PsiElement? {
        var parent = this.parent
        while (parent != null) {
            if (parent.checkFunctionType(languageDisplayName)) {
                return parent
            }
            parent = parent.parent
        }
        return null
    }

    override fun isAvailable(project: Project, editor: Editor?, element: PsiElement): Boolean {
        val functionElement = element.getNearestFunction(element.language.displayName)
        return functionElement?.checkFunctionType(element.language.displayName) ?: false
    }

    private fun getCompletionResponseFromOpenAI(
        functionContent: String,
        languageDisplayName: String
    ): Flow<TextCompletion> {
        val dotenv = dotenv()
        val envVar: String = dotenv["OPENAI_API_KEY"]
        val openAIClient = OpenAI(envVar)
        val documentationLanguage = when (languageDisplayName) {
            "Java" -> "JavaDoc"
            "Kotlin" -> "KDoc"
            else -> "documentation"
        }
        val completionRequest = CompletionRequest(
            model = ModelId("code-cushman-001"),
            prompt = "Return only the" + documentationLanguage + "for the function below that has been written in " +
                    languageDisplayName +
                    "\n " +
                    "```" + languageDisplayName + "\n" +
                    functionContent + "\n" +
                    "```"
        )
        return openAIClient.completions(completionRequest)
    }

    override fun invoke(project: Project, editor: Editor?, element: PsiElement) {
        val methodContext = element.getNearestFunction(element.language.displayName)
        if (editor != null && methodContext != null && methodContext.checkFunctionType(element.language.displayName)) {
//            val openAICompletionsFlow =
//                getCompletionResponseFromOpenAI(methodContext.text, element.language.displayName)
            editor.caretModel.moveToOffset(methodContext.startOffset)
//            val document = editor.document

//            openAICompletionsFlow.map { it -> WriteCommandAction.runWriteCommandAction(project, () -> {
//                document.re
//            }) } }
            TODO("Not yet implemented")
        }

    }
}