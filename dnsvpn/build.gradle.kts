plugins {
    alias(libs.plugins.androidApplication)
}

android {
    namespace = "com.arny.dnshostsgenerator.dnsvpn"
    compileSdk = libs.versions.android.compileSdk.get().toInt()

    defaultConfig {
        // Отдельный APK: основной DnsHostsGenerator этот модуль не использует.
        applicationId = "com.arny.dnshostsgenerator.dnsvpn"
        minSdk = libs.versions.android.minSdk.get().toInt()
        targetSdk = libs.versions.android.targetSdk.get().toInt()
        versionCode = 1
        versionName = "0.1.0"
    }

    buildTypes {
        release {
            isMinifyEnabled = false
        }
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

    testImplementation("junit:junit:4.13.2")
    testImplementation("org.jetbrains.kotlin:kotlin-test-junit")
}
