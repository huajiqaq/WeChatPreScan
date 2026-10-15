plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
}

android {
    namespace = "io.github.huajiqaq.wxprescan"
    compileSdk = 35

    defaultConfig {
        applicationId = "io.github.huajiqaq.wxprescan"
        minSdk = 24
        targetSdk = 35
        versionCode = 1
        versionName = "1.0"
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
            signingConfig = signingConfigs.getByName("debug")
        }
        getByName("debug") {
            isMinifyEnabled = false
        }
    }

    splits {
        abi {
            isEnable = false
            reset()
            include("arm64-v8a", "armeabi-v7a", "x86", "x86_64")
            isUniversalApk = false
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions {
        jvmTarget = "17"
    }

    packaging {
        jniLibs {
            useLegacyPackaging = false
        }
        resources {
            excludes += listOf(
                "META-INF/*.kotlin_module",
                "kotlin/**",
                "DebugProbesKt.bin",
                "META-INF/version-control-info.textproto",
                "kotlin-tooling-metadata.json"
            )
        }
    }

    lint {
        checkReleaseBuilds = false
        abortOnError = false
    }

    applicationVariants.configureEach {
        val vName = versionName
        outputs.configureEach {
            val out = this as com.android.build.gradle.internal.api.BaseVariantOutputImpl
            val abi = out.getFilter("ABI")
            val suffix = if (abi.isNullOrEmpty()) "" else "_$abi"
            out.outputFileName = "WeChatPreScan_${vName}${suffix}.apk"
        }
    }
}

dependencies {
    compileOnly(libs.libxposed.api)
    implementation(libs.dexkit)
}
