package com.github.iodine98.intellijdocumentationplugin.docintention

import com.aallam.openai.api.completion.CompletionRequest
import com.aallam.openai.api.completion.TextCompletion
import com.aallam.openai.api.model.ModelId
import com.aallam.openai.client.OpenAI
import com.intellij.codeInsight.intention.PsiElementBaseIntentionAction
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.Project
import com.intellij.psi.JavaPsiFacade
import com.intellij.psi.PsiComment
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiMethod
import kotlinx.coroutines.runBlocking
import org.jetbrains.kotlin.idea.quickfix.createFromUsage.callableBuilder.getReturnTypeReference
import org.jetbrains.kotlin.lombok.utils.capitalize
import org.jetbrains.kotlin.nj2k.postProcessing.type
import org.jetbrains.kotlin.psi.KtFunction
import org.jetbrains.kotlin.psi.KtPsiFactory
import org.jetbrains.kotlin.psi.psiUtil.getParentOfType


open class DocumentationIntention : PsiElementBaseIntentionAction() {
    override fun startInWriteAction(): Boolean = true

    override fun getText(): String = "Write documentation for this method"

    override fun getFamilyName(): String = "Write documentation"

    private fun getNearestFunction(element: PsiElement, languageDisplayName: String?): PsiElement? {
        return when (languageDisplayName) {
            "Java" -> element.getParentOfType<PsiMethod>(true)
            "Kotlin" -> element.getParentOfType<KtFunction>(true)
            else -> null
        }
    }

    override fun isAvailable(project: Project, editor: Editor?, element: PsiElement): Boolean {
        /**
         * Code generated using ChatGPT
         * With prompt: How to generate some Java documentation above a method using an IntentionAction in IntelliJ IDEA plugin development in Kotlin?
         */
        return getNearestFunction(element, element.language.displayName) != null
    }

    override fun invoke(project: Project, editor: Editor?, element: PsiElement) {
        /**
         * Code (partially) generated using ChatGPT
         * With prompt: How to generate some Java documentation above a method using an IntentionAction in IntelliJ IDEA plugin development in Kotlin?
         */
        val openAIEnabled: Boolean = System.getenv("OPENAI_ENABLED") == "TRUE"
        getNearestFunction(element, element.language.displayName)?.let { method ->
            val docCommentText = when (openAIEnabled) {
                true -> getDocStringFromOpenAI(method, method.language.displayName)
                false -> getDocStringStub(method)
            }
            val docComment: PsiComment? = when (method.language.displayName) {
                "Java" -> JavaPsiFacade.getElementFactory(project).createDocCommentFromText(docCommentText)
                "Kotlin" -> KtPsiFactory(project).createComment(docCommentText)
                else -> null
            }
            docComment?.let { method.addBefore(docComment, method.firstChild) }
        }
    }

    private fun getDocStringFromOpenAI(method: PsiElement, languageDisplayName: String): String = runBlocking {
        val textCompletion = getCompletionResponseFromOpenAI(
            functionContent = method.text,
            languageDisplayName = languageDisplayName
        )
        val response = textCompletion.choices[0].text.trim()
        val regexPattern = Regex("/\\*\\*([\\s\\S]*?)\\*/")
        val matcherResult = regexPattern.find(response)
        val commentText = matcherResult?.value ?: "No comment"
        commentText
    }

    private fun generateDocString(
        functionName: String,
        parameters: List<String>,
        parameterTypes: List<String>,
        returnType: String
    ): String = buildString {
        append("/**\n")
        append("* $functionName Method Description:\n")
        append("* \n")
        for ((index, param) in parameters.withIndex()) {
            val paramType = "type " + parameterTypes.getOrElse(index) { "" }
            append("* @param $param of $paramType \n")
            append("* @return $returnType \n")
            append("*/")
        }
    }

    private fun getDocStringStub(methodElement: PsiElement): String = when (methodElement.language.displayName) {
        "Java" -> getDocStringStubJava(methodElement)
        "Kotlin" -> getDocStringStubKotlin(methodElement)
        else -> ""
    }

    private fun getDocStringStubKotlin(methodElement: PsiElement): String {
        val methodSignature = methodElement as KtFunction
        val params = methodSignature.valueParameterList?.parameters?.map { it.name!! } ?: listOf()
        val paramTypes = methodSignature.valueParameterList?.parameters?.map { it.type().toString() } ?: listOf()
        val returnType = methodSignature.getReturnTypeReference()?.text ?: "void"
        val functionName = methodSignature.name?.capitalize() ?: "Unnamed Function"
        return generateDocString(
            functionName = functionName,
            parameters = params,
            parameterTypes = paramTypes,
            returnType = returnType
        )
    }

    private fun getDocStringStubJava(methodElement: PsiElement): String {
        val methodSignature = methodElement as PsiMethod
        val params = methodSignature.parameterList.parameters.map { it.name }
        val paramTypes = methodSignature.parameterList.parameters.map { it.type.canonicalText }
        val returnType = methodSignature.returnType?.canonicalText ?: "void"
        val functionName = methodSignature.name.capitalize()
        return generateDocString(
            functionName = functionName,
            parameters = params,
            parameterTypes = paramTypes,
            returnType = returnType
        )
    }

    private suspend fun getCompletionResponseFromOpenAI(
        functionContent: String,
        languageDisplayName: String,
        modelId: String = "text-davinci-003",
        openAIKey: String = "OPENAI_API_KEY"
    ): TextCompletion {
        val envVar: String = System.getenv(openAIKey)
        val openAIClient = OpenAI(envVar)
        val documentationLanguage = when (languageDisplayName) {
            "Java" -> "JavaDoc"
            "Kotlin" -> "KDoc"
            else -> "documentation"
        }

        val completionRequest = CompletionRequest(
            model = ModelId(modelId),
            prompt = buildString {
                append("Generate only the $documentationLanguage ")
                append("for the function below that has been written in")
                append("$languageDisplayName: \n")
                append("```$languageDisplayName\n")
                append("$functionContent\n")
                append("```")
            },
            temperature = 0.7,
            maxTokens = 256,
            topP = 1.0,
            frequencyPenalty = 0.0,
            presencePenalty = 0.0
        )
        return openAIClient.completion(completionRequest)
    }
}


