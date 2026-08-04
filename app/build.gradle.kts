plugins {
    alias(libs.plugins.android.application)
}

android {
    namespace = "com.hazbu.xpoint"
    compileSdk = 37

    defaultConfig {
        applicationId = "com.hazbu.xpoint"
        minSdk = 28
        targetSdk = 36
        versionCode = project.findProperty("APP_VERSION_CODE")?.toString()?.toInt() ?: System.getenv("APP_VERSION_CODE")?.toInt() ?: 1
        versionName = project.findProperty("APP_VERSION_NAME")?.toString() ?: System.getenv("APP_VERSION_NAME") ?: "1.0"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    signingConfigs {
        create("release") {
            val keystoreFile = System.getenv("RELEASE_STORE_FILE") ?: project.findProperty("RELEASE_STORE_FILE")?.toString()
            val keystorePassword = System.getenv("RELEASE_STORE_PASSWORD") ?: project.findProperty("RELEASE_STORE_PASSWORD")?.toString()
            val keyAlias = System.getenv("RELEASE_KEY_ALIAS") ?: project.findProperty("RELEASE_KEY_ALIAS")?.toString()
            val keyPassword = System.getenv("RELEASE_KEY_PASSWORD") ?: project.findProperty("RELEASE_KEY_PASSWORD")?.toString()

            if (keystoreFile != null) {
                storeFile = file(keystoreFile)
                storePassword = keystorePassword
                this.keyAlias = keyAlias
                this.keyPassword = keyPassword
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            signingConfig = signingConfigs.getByName("release")
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
}

dependencies {
    compileOnly(libs.libxposed.api)
    implementation(libs.androidx.appcompat)
    implementation(libs.androidx.core.ktx)
    implementation(libs.material)
    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(libs.androidx.junit)
}
