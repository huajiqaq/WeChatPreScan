import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
}

/** 签名参数只放在本机 local.properties（已忽略），不进入仓库。 */
val signingProps = Properties().apply {
    val file = rootProject.file("local.properties")
    if (file.isFile) file.inputStream().use { load(it) }
}

android {
    namespace = "io.github.huajiqaq.wxprescan"
    compileSdk = 35

    defaultConfig {
        applicationId = "io.github.huajiqaq.wxprescan"
        minSdk = 24
        targetSdk = 35
        versionCode = 2
        versionName = "1.1"
    }

    signingConfigs {
        val storePath = signingProps.getProperty("signing.storeFile")
        if (!storePath.isNullOrBlank() && rootProject.file(storePath).isFile) {
            create("release") {
                storeFile = rootProject.file(storePath)
                storePassword = signingProps.getProperty("signing.storePassword")
                keyAlias = signingProps.getProperty("signing.keyAlias")
                keyPassword = signingProps.getProperty("signing.keyPassword")
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
            signingConfig = signingConfigs.findByName("release") ?: signingConfigs.getByName("debug")
        }
        getByName("debug") {
            isMinifyEnabled = false
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
            out.outputFileName = "WeChatPreScan_${vName}.apk"
        }
    }
}

dependencies {
    compileOnly(libs.libxposed.api)
    implementation(libs.dexkit)
    testImplementation(libs.junit)
}
