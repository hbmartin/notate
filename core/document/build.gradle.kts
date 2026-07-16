plugins {
    id("com.android.library")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.serialization")
}

android {
    namespace = "com.alexdremov.notate.core.document"

    buildFeatures { buildConfig = true }

    testOptions {
        unitTests {
            isIncludeAndroidResources = true
            isReturnDefaultValues = true
            all { it.maxHeapSize = "2g" }
        }
    }
}

dependencies {
    api(project(":core:app-contracts"))
    implementation("androidx.core:core-ktx:1.17.0")
    implementation("androidx.documentfile:documentfile:1.1.0")
    implementation("androidx.work:work-runtime-ktx:2.10.0")
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.10.0")
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-protobuf:1.10.0")
    api("com.onyx.android.sdk:onyxsdk-pen:1.5.4")
    api("com.onyx.android.sdk:onyxsdk-base:1.8.5")
    implementation("io.noties.markwon:core:4.6.2")
    implementation("io.noties.markwon:ext-strikethrough:4.6.2")
    implementation("io.noties.markwon:ext-tables:4.6.2")
    implementation("io.noties.markwon:ext-tasklist:4.6.2")

    testImplementation("junit:junit:4.13.2")
    testImplementation("org.robolectric:robolectric:4.16")
    testImplementation("io.mockk:mockk:1.14.7")
    testImplementation("com.google.truth:truth:1.4.5")
    testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.9.0")
    testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.9.0")
    testImplementation("androidx.work:work-testing:2.10.0")
    testImplementation("androidx.test:core-ktx:1.6.1")
}
