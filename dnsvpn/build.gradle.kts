plugins {
    // Библиотека: экран и запуск службы теперь в основном приложении.
    alias(libs.plugins.androidApplication)
}

android {
    namespace = "com.arny.dnshostsgenerator.dnsvpn"
    compileSdk = libs.versions.android.compileSdk.get().toInt()

    defaultConfig {
        applicationId = "com.arny.dnshostsgenerator.dnsvpn"
        versionCode = 1
        versionName = "0.1.0"
        targetSdk = libs.versions.android.targetSdk.get().toInt()
        minSdk = libs.versions.android.minSdk.get().toInt()
    }


    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}

dependencies {
    // Переиспользуем DoT-резолвер из основного проекта: свой код, а не сторонний.
    implementation(projects.shared)
    implementation(libs.dnsjava)
    implementation(libs.kotlinx.coroutinesCore)
    implementation(libs.koin.core)
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.8.7")

    testImplementation("junit:junit:4.13.2")
    testImplementation("org.jetbrains.kotlin:kotlin-test-junit")
}
