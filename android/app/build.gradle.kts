import java.io.FileInputStream
import java.util.Properties

plugins {
    id("com.android.application")
    id("kotlin-android")
    // The Flutter Gradle Plugin must be applied after the Android and Kotlin Gradle plugins.
    id("dev.flutter.flutter-gradle-plugin")
    id("kotlin-kapt")
}

android {
    namespace = "com.kadamshubham098.pulse"
    compileSdk = flutter.compileSdkVersion
    ndkVersion = flutter.ndkVersion

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions {
        jvmTarget = JavaVersion.VERSION_17.toString()
    }

    defaultConfig {
        // TODO: Specify your own unique Application ID (https://developer.android.com/studio/build/application-id.html).
        applicationId = "com.kadamshubham098.pulse"
        // You can update the following values to match your application needs.
        // For more information, see: https://flutter.dev/to/review-gradle-config.
        minSdk = flutter.minSdkVersion
        targetSdk = flutter.targetSdkVersion
        versionCode = flutter.versionCode
        versionName = flutter.versionName
    }

    val keystorePropertiesFile = rootProject.file("key.properties")
    val hasReleaseKeystore = keystorePropertiesFile.exists()

    if (hasReleaseKeystore) {
        val keystoreProperties = Properties()
        FileInputStream(keystorePropertiesFile).use { keystoreProperties.load(it) }

        signingConfigs {
            create("release") {
                val storeFilePath =
                    keystoreProperties.getProperty("storeFile")
                        ?: error("Missing `storeFile` in android/key.properties")
                storeFile = rootProject.file(storeFilePath)
                storePassword =
                    keystoreProperties.getProperty("storePassword")
                        ?: error("Missing `storePassword` in android/key.properties")
                keyAlias =
                    keystoreProperties.getProperty("keyAlias")
                        ?: error("Missing `keyAlias` in android/key.properties")
                keyPassword =
                    keystoreProperties.getProperty("keyPassword")
                        ?: error("Missing `keyPassword` in android/key.properties")
            }
        }
    }

    buildTypes {
        release {
            signingConfig = if (hasReleaseKeystore) {
                signingConfigs.getByName("release")
            } else {
                // Fallback for local dev; for distribution you should provide `android/key.properties`.
                signingConfigs.getByName("debug")
            }
        }
    }
}

flutter {
    source = "../.."
}

kapt {
    correctErrorTypes = true
}

dependencies {
    val room_version = "2.5.2"

    implementation("androidx.room:room-runtime:$room_version")
    kapt("androidx.room:room-compiler:$room_version")
    implementation("androidx.room:room-ktx:$room_version")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.6.4")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.6.4")
}
