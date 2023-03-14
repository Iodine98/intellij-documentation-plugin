package com.github.iodine98.intellijdocumentationplugin.docintention

import com.aallam.openai.api.completion.CompletionRequest
import com.aallam.openai.api.completion.TextCompletion
import com.aallam.openai.api.model.ModelId
import com.aallam.openai.client.OpenAI
import com.intellij.codeInsight.intention.IntentionAction
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.Project
import com.intellij.psi.JavaPsiFacade
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import com.intellij.psi.PsiMethod
import kotlinx.coroutines.runBlocking
import org.jetbrains.kotlin.idea.quickfix.createFromUsage.callableBuilder.getReturnTypeReference
import org.jetbrains.kotlin.lombok.utils.capitalize
import org.jetbrains.kotlin.nj2k.postProcessing.type
import org.jetbrains.kotlin.psi.KtFunction
import org.jetbrains.kotlin.psi.KtPsiFactory
import org.jetbrains.kotlin.psi.psiUtil.getParentOfType


open class DocumentationIntention : IntentionAction {
    override fun startInWriteAction(): Boolean = true

    override fun getText(): String = "Write documentation for this method"

    override fun getFamilyName(): String = "Write documentation"

    private fun getNearestFunction(element: PsiElement?, languageDisplayName: String?): PsiElement? {
        return when (languageDisplayName) {
            "Java" -> element?.getParentOfType<PsiMethod>(true)
            "Kotlin" -> element?.getParentOfType<KtFunction>(true)
            else -> null
        }
    }

    override fun isAvailable(project: Project, editor: Editor?, file: PsiFile?): Boolean {
        /**
         * Code generated using ChatGPT
         * With prompt: How to generate some Java documentation above a method using an IntentionAction in IntelliJ IDEA plugin development in Kotlin?
         */
        val element = file?.findElementAt(editor?.caretModel?.offset ?: return false)
        return getNearestFunction(element, element?.language?.displayName) != null
    }

    override fun invoke(project: Project, editor: Editor?, file: PsiFile?) {
        /**
         * Code (partially) generated using ChatGPT
         * With prompt: How to generate some Java documentation above a method using an IntentionAction in IntelliJ IDEA plugin development in Kotlin?
         */
        val element = file?.findElementAt(editor?.caretModel?.offset ?: return)
        val methodContext = getNearestFunction(element, element?.language?.displayName)
        val openAIEnabled: Boolean = System.getenv("OPENAI_ENABLED") == "TRUE"
        methodContext?.let { method ->
            runBlocking {
                val languageDisplayName = methodContext.language.displayName
                val docCommentText = when (openAIEnabled) {
                    true -> {
                        val textCompletion = getCompletionResponseFromOpenAI(
                            methodContext.text,
                            languageDisplayName
                        )
                        val response = textCompletion.choices[0].text.trim()
                        val regexPattern = Regex("/\\*\\*(\\s|\\*|\\w|[\\[\\],.@#])+\\*/")
                        regexPattern.find(response)?.value ?: ""
                    }

                    false -> getDocStringStub(method)
                }
                when (languageDisplayName) {
                    "Java" -> {
                        val documentationFactory = JavaPsiFacade.getElementFactory(project)
                        val newDocComment = documentationFactory.createDocCommentFromText(docCommentText)
                        method.addBefore(newDocComment, method.firstChild)
                    }

                    "Kotlin" -> {
                        val documentationFactory = KtPsiFactory(project)
                        val newDocComment = documentationFactory.createComment(docCommentText)
                        method.addBefore(newDocComment, method.firstChild)
                    }

                    else -> {}
                }

            }
        }
    }
}

private fun generateDocString(
    functionName: String,
    parameters: List<String>,
    parameterTypes: List<String>,
    returnType: String
): String {
    var docString = "/**\n"
    docString += "* $functionName Method Description:\n"
    docString += "* \n"
    for ((index, param) in parameters.withIndex()) {
        val paramType = "type " + parameterTypes.getOrElse(index) { "" }
        docString += "* @param $param of $paramType \n"
    }
    docString += "* @return $returnType \n"
    docString += "*/"
    return docString
}

private fun getDocStringStub(methodElement: PsiElement): String {
    if (methodElement.language.displayName == "Java") {
        val methodSignature = methodElement as PsiMethod
        val params = methodSignature.parameterList.parameters.map { it.name }
        val paramTypes = methodSignature.parameterList.parameters.map { it.type.canonicalText }
        val returnType = methodSignature.returnType?.canonicalText ?: "void"
        val functionName = methodSignature.name.capitalize()
        return generateDocString(functionName, params, paramTypes, returnType)

    }
    if (methodElement.language.displayName == "Kotlin") {
        val methodSignature = methodElement as KtFunction
        val params = methodSignature.valueParameterList?.parameters?.map { it.name!! } ?: listOf()
        val paramTypes = methodSignature.valueParameterList?.parameters?.map { it.type().toString() } ?: listOf()
        val returnType = methodSignature.getReturnTypeReference()?.text ?: "void"
        val functionName = methodSignature.name?.capitalize() ?: "Unnamed Function"
        return generateDocString(functionName, params, paramTypes, returnType)
    }
    return ""
}

private suspend fun getCompletionResponseFromOpenAI(
    functionContent: String,
    languageDisplayName: String,
    modelId: String = "text-davinci-003"
): TextCompletion {
    val envVar: String = System.getenv("OPENAI_API_KEY")
    val openAIClient = OpenAI(envVar)
    val documentationLanguage = when (languageDisplayName) {
        "Java" -> "JavaDoc"
        "Kotlin" -> "KDoc"
        else -> "documentation"
    }
    val completionRequest = CompletionRequest(
        model = ModelId(modelId),
        prompt = "Generate only the " + documentationLanguage + " for the function below that has been written in " +
                languageDisplayName +
                "\n " +
                "```" + languageDisplayName + "\n" +
                functionContent + "\n" +
                "```",
        temperature = 0.7,
        maxTokens = 256,
        topP = 1.0,
        frequencyPenalty = 0.0,
        presencePenalty = 0.0
    )
    return openAIClient.completion(completionRequest)
}