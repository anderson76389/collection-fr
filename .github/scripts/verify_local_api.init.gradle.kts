import org.gradle.api.attributes.Usage

gradle.projectsEvaluated {
    val root = gradle.rootProject
    root.tasks.register("verifyLocalExtensionApis") {
        group = "verification"
        description = "Verify that both extension API artifacts resolve from the checked-in Maven repository."
        doLast {
            val repository = root.file("vendor/extensions-lib/maven/com/github/keiyoushi/extensions-lib")
            val versions = repository.listFiles()?.filter { it.isDirectory }.orEmpty()
            check(versions.size == 2) { "Expected the two preserved extension API versions" }
            versions.sortedBy { it.name }.forEach { directory ->
                val dependency = root.dependencies.create("com.github.keiyoushi:extensions-lib:${directory.name}@aar")
                val configuration = root.configurations.detachedConfiguration(dependency).apply {
                    isTransitive = false
                    attributes.attribute(Usage.USAGE_ATTRIBUTE, root.objects.named(Usage::class.java, Usage.JAVA_API))
                }
                val resolved = configuration.singleFile.canonicalFile
                val expected = directory.resolve("extensions-lib-${directory.name}.aar").canonicalFile
                check(resolved == expected) { "Extension API resolved outside the local repository: $resolved" }
                println("LOCAL_EXTENSION_API ${directory.name}: $resolved")
            }
        }
    }
}
