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

    override fun getText(): String = "Generate documentation for this method"

    override fun getFamilyName(): String = "Write documentation"

    /**
     * Find the closes parent function that is in the scope of the current PsiElement
     */
    private fun getNearestFunction(element: PsiElement, languageDisplayName: String?): PsiElement? {
        return when (languageDisplayName) {
            "Java" -> element.getParentOfType<PsiMethod>(true)
            "Kotlin" -> element.getParentOfType<KtFunction>(true)
            else -> null
        }
    }

    /**
     * If an element is hovered over with the cursor, then trigger this method to check if intention should appear
     */
    override fun isAvailable(project: Project, editor: Editor?, element: PsiElement): Boolean {
        return getNearestFunction(element, element.language.displayName) != null
    }

    /**
     * If intention is selected, trigger this method
     *
     */
    override fun invoke(project: Project, editor: Editor?, element: PsiElement) {

        val openAIEnabled: Boolean = System.getenv("OPENAI_ENABLED") == "TRUE" // Use either a stubbed documentation by setting OPENAI_ENABLED = FALSE or call the OpenAI documentation
        getNearestFunction(element, element.language.displayName)?.let { method ->
            val docCommentText = when (openAIEnabled) {
                // Depending on openAIEnabled either call API or generate stub
                true -> getDocStringFromOpenAI(method, method.language.displayName)
                false -> getDocStringStub(method)
            }
            val docComment: PsiComment? = when (method.language.displayName) {
                // Call different methods based on programming language
                "Java" -> JavaPsiFacade.getElementFactory(project).createCommentFromText(docCommentText, method)
                "Kotlin" -> KtPsiFactory(project).createComment(docCommentText)
                else -> null
            }
            docComment?.let { method.addBefore(docComment, method.firstChild) }
        }
    }

    /**
     * This method generates documentation using an unofficial Kotlin library to connect to OpenAI REST APIs
     * Since the getCompletionResponseFromOpenAI method has a `suspend` modifier, I use runBlocking to force the thread to wait until it gets the response from OpenAI
     */
    private fun getDocStringFromOpenAI(method: PsiElement, languageDisplayName: String): String = runBlocking {
        val textCompletion = getCompletionResponseFromOpenAI(
            functionContent = method.text,
            languageDisplayName = languageDisplayName
        )
        val response = textCompletion.choices[0].text.trim() // Trim any whitespace to suit the addComment function calls
        val regexPattern = Regex("/\\*\\*([\\s\\S]*?)\\*/") // Regex generated using chatGPT to parse KDoc/JavaDoc comments
        val matcherResult = regexPattern.find(response)
        val commentText = matcherResult?.value ?: "No comment" // Elvis operator to at least return something if the above expression were to fail
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

    /**
     * Get the DocStringStub based on the programming language
     */
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
        val envVar: String = System.getenv(openAIKey) // Get the ENVIRONMENT VARIABLE for the OPENAI_API_KEY
        val openAIClient = OpenAI(envVar) // initialize the client
        val documentationLanguage = when (languageDisplayName) {
            "Java" -> "JavaDoc"
            "Kotlin" -> "KDoc"
            else -> "documentation"
        } // Change the documentation vocabulary based on programming language

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
            temperature = 0.7, // These are recommended settings by OpenAI
            maxTokens = 256,
            topP = 1.0,
            frequencyPenalty = 0.0,
            presencePenalty = 0.0
        )
        return openAIClient.completion(completionRequest)
    }
}


