# intellij-documentation-plugin

<!-- Plugin description -->
This plugin attempts to generate KDoc (for Kotlin) or JavaDoc (for Java) based on the function/method body.
This plugin has two modes:
1. AI mode: let GPT-3.5 (the model that runs ChatGPT) generate documentation for you
2. Manual mode: write KDoc/JavaDoc based on the types of the arguments of the function/method and its return type.

<!-- Plugin description end -->

## How to run the plugin

1. Clone the repository
2. Open in IntelliJ
3. Ensure that the SDK is set to at least JDK 17 or higher in terms of version
4. Build Gradle project (this will happen automatically in IntelliJ)
5. Add an `.env` file with the following two parameters:
    1. `OPENAI_API_KEY` which holds your API key for access to OpenAI's models. Configure your `.env` file like this:  `OPENAI_API_KEY=<your key>`
   2. `OPENAI_ENABLED` which causes the plugin to call the OpenAI API endpoint. If this variable does not equal `TRUE`, the plugin will default to the standard documentation stub. Configure your `.env` as such: `OPENAI_ENABLED=TRUE` 
6. Repeat step 4. to ensure that the `runIde` task in Gradle exports the variables from the `.env` file to the JVM
7. Select the `Run Plugin` configuration
8. Set the cursor to the method definition or inside of it and trigger the intention.
9. Select `generate documentation for this method`
10. Observe the magic!