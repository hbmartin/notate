import org.gradle.api.DefaultTask
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.tasks.InputFile
import org.gradle.api.tasks.PathSensitive
import org.gradle.api.tasks.PathSensitivity
import org.gradle.api.tasks.TaskAction
import java.io.FileInputStream
import java.security.MessageDigest
import java.util.Properties

abstract class VerifyCanonicalLibcxx : DefaultTask() {
    @get:InputFile
    @get:PathSensitive(PathSensitivity.NONE)
    abstract val expectedLibrary: RegularFileProperty

    @get:InputFile
    @get:PathSensitive(PathSensitivity.NONE)
    abstract val packagedLibrary: RegularFileProperty

    @TaskAction
    fun verify() {
        val expected = expectedLibrary.get().asFile
        val packaged = packagedLibrary.get().asFile
        val digest = MessageDigest.getInstance("SHA-256")
        fun sha256(file: File): String = digest.digest(file.readBytes()).joinToString("") { "%02x".format(it) }

        check(sha256(packaged) == sha256(expected)) {
            "The packaged libc++_shared.so does not match the canonical NDK runtime"
        }
    }
}

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "com.alexdremov.notate"
    compileSdk = 36
    ndkVersion = "28.2.13676358"

    defaultConfig {
        applicationId = "com.alexdremov.notate"
        minSdk = 26
        targetSdk = 36
        versionCode = 1
        versionName = "1.0"
        ndk {
            abiFilters += "arm64-v8a"
        }
        vectorDrawables {
            useSupportLibrary = true
        }
    }

    signingConfigs {
        create("release") {
            val keystoreFile = rootProject.file("keystore.properties")
            if (keystoreFile.exists()) {
                val properties = Properties()
                properties.load(FileInputStream(keystoreFile))
                storeFile = file(properties["storeFile"] as String)
                storePassword = properties["storePassword"] as String
                keyAlias = properties["keyAlias"] as String
                keyPassword = properties["keyPassword"] as String
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            signingConfig = signingConfigs.getByName("release")
        }
        debug {
            // Standard debug build uses default ~/.android/debug.keystore
            enableUnitTestCoverage = true
        }
    }

    buildFeatures {
        buildConfig = true
    }

    lint {
        disable += "MutableCollectionMutableState"
        disable += "AutoboxingStateCreation"
    }

    kotlin {
        jvmToolchain(21)
    }

    testOptions {
        unitTests {
            isIncludeAndroidResources = true
            isReturnDefaultValues = true
            all {
                it.maxHeapSize = "2g"
            }
        }
    }

    packaging {
        jniLibs {
            pickFirsts.add("lib/**/libc++_shared.so")
            // GitHub distributes a standalone APK, so compress native libraries in the
            // download and let Android extract them during installation.
            useLegacyPackaging = true
        }
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
            excludes += "META-INF/DEPENDENCIES"
            excludes += "META-INF/LICENSE"
            excludes += "META-INF/LICENSE.txt"
            excludes += "META-INF/license.txt"
            excludes += "META-INF/NOTICE"
            excludes += "META-INF/NOTICE.txt"
            excludes += "META-INF/notice.txt"
            excludes += "META-INF/ASL2.0"
            excludes += "META-INF/*.kotlin_module"
            excludes += "META-INF/INDEX.LIST"
        }
    }
}

val canonicalLibcxx =
    fileTree(android.ndkDirectory.resolve("toolchains/llvm/prebuilt")) {
        include("*/sysroot/usr/lib/aarch64-linux-android/libc++_shared.so")
    }.singleFile
val canonicalJniRoot = layout.buildDirectory.dir("generated/canonicalJniLibs")
val prepareCanonicalLibcxx by
    tasks.registering(Copy::class) {
        from(canonicalLibcxx)
        into(canonicalJniRoot.map { it.dir("arm64-v8a") })
    }

android.sourceSets.getByName("main").jniLibs.srcDir(canonicalJniRoot.get().asFile)
tasks.matching { it.name.matches(Regex("merge(Debug|Release)JniLibFolders")) }.configureEach {
    dependsOn(prepareCanonicalLibcxx)
}

listOf("Debug" to "debug", "Release" to "release").forEach { (variantName, variantDirectory) ->
    val verifyTask =
        tasks.register<VerifyCanonicalLibcxx>("verify${variantName}CanonicalLibcxx") {
            dependsOn("merge${variantName}NativeLibs")
            expectedLibrary.fileValue(canonicalLibcxx)
            packagedLibrary.set(
                layout.buildDirectory.file(
                    "intermediates/merged_native_libs/$variantDirectory/merge${variantName}NativeLibs/out/lib/arm64-v8a/libc++_shared.so",
                ),
            )
        }
    tasks.matching { it.name == "package$variantName" }.configureEach { dependsOn(verifyTask) }
}

dependencies {
    implementation(project(":core:app-contracts"))
    implementation(project(":core:document"))
    implementation(project(":feature:text-recognition"))
    implementation(project(":feature:pdf"))
    implementation(project(":feature:sync"))
    implementation(project(":feature:home"))
    implementation(project(":feature:canvas"))
    implementation("com.onyx.android.sdk:onyxsdk-base:1.8.5")
    implementation("org.lsposed.hiddenapibypass:hiddenapibypass:6.1")

    // Vulnerability force fixes
    constraints {
        // See: https://github.com/alexdremov/notate/security/dependabot/2
        implementation("com.squareup.retrofit2:retrofit") {
            because("Fix XXE vulnerability in Retrofit versions < 2.5.0")
            version {
                strictly("[2.5.0,)")
            }
        }

        // See: https://github.com/alexdremov/notate/security/dependabot/12
        implementation("commons-io:commons-io") {
            because("Fix vulnerability in commons-io versions < 2.14.0")
            version {
                strictly("[2.14.0,)")
            }
        }

        // See: https://github.com/alexdremov/notate/security/dependabot/11
        implementation("com.google.protobuf:protobuf-kotlin") {
            because("Fix vulnerability in com.google.protobuf:protobuf-kotlin < 3.25.5")
            version {
                strictly("[3.25.5,)")
            }
        }
    }

    modules {
        module("org.jetbrains:annotations-java5") {
            replacedBy("org.jetbrains:annotations", "annotations-java5 is a subset of annotations and causes duplicate classes")
        }
    }
}
