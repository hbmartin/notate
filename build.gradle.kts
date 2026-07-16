import com.android.build.api.dsl.LibraryExtension
import org.gradle.api.tasks.testing.Test
import org.gradle.testing.jacoco.plugins.JacocoTaskExtension
import org.gradle.testing.jacoco.tasks.JacocoReport
import org.jetbrains.kotlin.gradle.dsl.KotlinAndroidProjectExtension

plugins {
    id("com.android.application") version "9.2.1" apply false
    id("com.android.library") version "9.2.1" apply false
    id("org.jetbrains.kotlin.android") version "2.2.10" apply false
    id("org.jetbrains.kotlin.plugin.compose") version "2.2.10" apply false
    id("org.jetbrains.kotlin.plugin.serialization") version "2.2.0" apply false
    id("com.google.devtools.ksp") version "2.2.10-2.0.2" apply false
    id("jacoco")
}

val coverageProjectPaths =
    setOf(
        ":app",
        ":ocr-runtime",
        ":core:app-contracts",
        ":core:document",
        ":core:rendering",
        ":feature:text-recognition",
        ":feature:pdf",
        ":feature:sync",
        ":feature:home",
        ":feature:canvas",
    )

subprojects {
    pluginManager.withPlugin("com.android.library") {
        extensions.configure<LibraryExtension> {
            compileSdk = 36
            defaultConfig { minSdk = 26 }

            if (path in coverageProjectPaths) {
                buildTypes.named("debug") { enableUnitTestCoverage = true }
            }
        }
    }

    pluginManager.withPlugin("org.jetbrains.kotlin.android") {
        extensions.configure<KotlinAndroidProjectExtension> { jvmToolchain(21) }
    }

    if (path in coverageProjectPaths) {
        pluginManager.apply("jacoco")
        tasks.withType<Test>().configureEach {
            extensions.configure<JacocoTaskExtension> {
                isIncludeNoLocationClasses = true
                excludes = listOf("jdk.internal.*")
            }
        }
    }
}

tasks.register<JacocoReport>("jacocoTestReport") {
    val coverageProjects = coverageProjectPaths.map(project::project)
    dependsOn(coverageProjects.map { "${it.path}:testDebugUnitTest" })

    reports {
        xml.required.set(true)
        html.required.set(true)
    }

    sourceDirectories.from(coverageProjects.map { it.file("src/main/java") })
    classDirectories.from(
        coverageProjects.flatMap { module ->
            listOf(
                module.fileTree("build/tmp/kotlin-classes/debug") {
                    exclude(
                        "**/R.class",
                        "**/R$*.class",
                        "**/BuildConfig.*",
                        "**/Manifest*.*",
                        "**/*Test*.*",
                        "android/**/*.*",
                    )
                },
                module.fileTree("build/intermediates/javac/debug/classes") {
                    exclude(
                        "**/R.class",
                        "**/R$*.class",
                        "**/BuildConfig.*",
                        "**/Manifest*.*",
                        "**/*Test*.*",
                        "android/**/*.*",
                    )
                },
            )
        },
    )
    executionData.from(
        coverageProjects.map { module ->
            module.layout.buildDirectory.file("outputs/unit_test_code_coverage/debugUnitTest/testDebugUnitTest.exec")
        },
    )
}
