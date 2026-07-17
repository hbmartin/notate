plugins {
    id("com.android.library")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.serialization")
    id("com.google.devtools.ksp")
}

ksp { arg("room.schemaLocation", "$projectDir/schemas") }

android {
    namespace = "com.alexdremov.notate.feature.textrecognition"
    testOptions { unitTests.isIncludeAndroidResources = true }
}

dependencies {
    api(project(":core:document"))
    api(project(":ocr-runtime"))
    implementation("androidx.room:room-runtime:2.8.4")
    implementation("androidx.room:room-ktx:2.8.4")
    implementation("androidx.documentfile:documentfile:1.1.0")
    implementation("androidx.work:work-runtime-ktx:2.10.0")
    implementation("com.google.mlkit:digital-ink-recognition:19.0.0")
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-protobuf:1.10.0")
    ksp("androidx.room:room-compiler:2.8.4")

    testImplementation("junit:junit:4.13.2")
    testImplementation("org.robolectric:robolectric:4.16")
    testImplementation("io.mockk:mockk:1.14.7")
    testImplementation("com.google.truth:truth:1.4.5")
    testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.9.0")
    testImplementation("androidx.work:work-testing:2.10.0")
    testImplementation("androidx.test:core-ktx:1.6.1")
}
