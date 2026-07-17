plugins {
    id("com.android.library")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
}

android {
    namespace = "com.alexdremov.notate.feature.home"
    buildFeatures { compose = true }
}

dependencies {
    api(project(":core:app-contracts"))
    implementation(project(":core:document"))
    implementation(project(":core:rendering"))
    implementation(project(":feature:text-recognition"))
    implementation(project(":feature:sync"))
    implementation("androidx.activity:activity-compose:1.12.2")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.10.0")
    implementation("androidx.lifecycle:lifecycle-viewmodel-ktx:2.10.0")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.10.0")
    implementation("androidx.documentfile:documentfile:1.1.0")
    implementation(platform("androidx.compose:compose-bom:2026.01.00"))
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-graphics")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended")
    implementation("androidx.navigation:navigation-compose:2.9.6")
    implementation("com.onyx.android.sdk:onyxsdk-base:1.8.5")
    implementation("com.google.android.gms:play-services-auth:21.5.0")
    implementation("com.google.apis:google-api-services-drive:v3-rev20251210-2.0.0")
}
