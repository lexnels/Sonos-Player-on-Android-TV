import java.util.Properties

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
}

// Signing details are kept out of the repository; without them only debug builds work.
val keystorePropertiesFile = rootProject.file("keystore.properties")
val keystoreProperties = Properties().apply {
    if (keystorePropertiesFile.exists()) {
        keystorePropertiesFile.inputStream().use { load(it) }
    }
}

android {
    namespace = "com.sonostv"
        compileSdk = 35
        buildToolsVersion = "34.0.0"

        defaultConfig {
            applicationId = "com.sonostv"
            minSdk = 23
            targetSdk = 35
        versionCode = 10
        versionName = "0.9"
    }

    signingConfigs {
        if (keystorePropertiesFile.exists()) {
            create("release") {
                storeFile = rootProject.file(keystoreProperties.getProperty("storeFile"))
                storePassword = keystoreProperties.getProperty("storePassword")
                keyAlias = keystoreProperties.getProperty("keyAlias")
                keyPassword = keystoreProperties.getProperty("keyPassword")
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            signingConfig = signingConfigs.findByName("release")
        }
    }

    applicationVariants.all {
        val variant = this
        outputs.all {
            val output = this as com.android.build.gradle.internal.api.BaseVariantOutputImpl
            output.outputFileName = "SonosTV-${variant.versionName}(${variant.versionCode})-${variant.name}.apk"
        }

        val capitalized = variant.name.replaceFirstChar { it.titlecase() }
        val copyApk = tasks.register("copy${capitalized}ApkToReleases") {
            dependsOn(variant.packageApplicationProvider)
            doLast {
                val dest = rootProject.file("releases").apply { mkdirs() }
                dest.listFiles()
                    ?.filter { it.isFile && it.name.endsWith("-${variant.name}.apk") }
                    ?.forEach { it.delete() }
                variant.outputs.forEach { output ->
                    val apk = output.outputFile
                    check(apk.exists()) { "Expected packaged APK at ${apk.absolutePath}" }
                    apk.copyTo(dest.resolve(apk.name), overwrite = true)
                }
            }
        }
        tasks.named("package$capitalized").configure { finalizedBy(copyApk) }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions {
        jvmTarget = "17"
    }

    buildFeatures {
        compose = true
    }
}

dependencies {
    val composeBom = platform("androidx.compose:compose-bom:2024.09.03")
    implementation(composeBom)

    implementation("androidx.core:core-ktx:1.13.1")
    implementation("androidx.media:media:1.7.0")
    implementation("androidx.activity:activity-compose:1.9.2")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.8.6")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.8.6")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.8.6")
    // The screensaver is a service, so it wires up Compose's owners itself.
    implementation("androidx.savedstate:savedstate-ktx:1.2.1")

    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.foundation:foundation")
    implementation("androidx.compose.animation:animation")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.graphics:graphics-shapes:1.0.1")
    implementation("androidx.compose.material:material-icons-extended")

    implementation("io.coil-kt:coil-compose:2.7.0")
    implementation("com.squareup.okhttp3:okhttp:4.12.0")
    implementation("com.google.zxing:core:3.5.3")

    debugImplementation("androidx.compose.ui:ui-tooling")
}
