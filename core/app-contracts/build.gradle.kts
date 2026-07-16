plugins {
    id("com.android.library")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "com.alexdremov.notate.core.contracts"
}

dependencies {
    api("androidx.work:work-runtime-ktx:2.10.0")
}
