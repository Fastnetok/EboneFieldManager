plugins {

    alias(libs.plugins.android.application)

    id("com.google.gms.google-services")

}

android {

    namespace =
        "com.fastnet.ebonefieldmanager"

    compileSdk = 34

    signingConfigs {

        create("release") {

            val isGitHub = System.getenv("GITHUB_ACTIONS") == "true"

            storeFile = if (isGitHub) {
                file("keystore.jks")
            } else {
                file("D:/AndroidKeys/EboneReleaseKey.jks")
            }

            storePassword = System.getenv("STORE_PASSWORD") ?: "aeiougabbas"

            keyAlias = System.getenv("KEY_ALIAS") ?: "ebone"

            keyPassword = System.getenv("KEY_PASSWORD") ?: "aeiougabbas"
        }
    }

    defaultConfig {

        applicationId =
            "com.fastnet.ebonefieldmanager"

        minSdk = 24

        targetSdk = 34

        versionCode = 5

        versionName = "1.0.5"

        testInstrumentationRunner =
            "androidx.test.runner.AndroidJUnitRunner"
    }

    buildTypes {

        // NEW: debug builds now use the SAME keystore as release, so a
        // debug APK built on this PC and one built by GitHub Actions
        // always share the same signature — required for in-app updates
        // to install over an existing copy instead of "App not installed".
        debug {
            signingConfig = signingConfigs.getByName("release")
        }

        release {

            signingConfig = signingConfigs.getByName("release")

            isMinifyEnabled = false

            proguardFiles(

                getDefaultProguardFile(
                    "proguard-android-optimize.txt"
                ),

                "proguard-rules.pro"
            )
        }
    }

    compileOptions {

        sourceCompatibility =
            JavaVersion.VERSION_17

        targetCompatibility =
            JavaVersion.VERSION_17
    }
}

dependencies {

    implementation(
        "androidx.core:core-ktx:1.13.1"
    )

    implementation(
        "androidx.appcompat:appcompat:1.7.0"
    )

    implementation(
        "com.google.android.material:material:1.12.0"
    )

    implementation(
        "androidx.activity:activity-ktx:1.9.0"
    )

    implementation(
        "androidx.constraintlayout:constraintlayout:2.1.4"
    )

    implementation(
        "androidx.recyclerview:recyclerview:1.3.2"

    )

    implementation(
        "com.google.android.gms:play-services-location:21.3.0"
    )

    // FIREBASE

    implementation(
        "com.google.firebase:firebase-database-ktx:21.0.0"
    )

    implementation(
        "com.google.firebase:firebase-messaging-ktx:24.0.1"
    )

    implementation(
        "com.google.firebase:firebase-auth-ktx:22.3.1"
    )

    // NETWORKING - required by VersionChecker.kt for GitHub API calls
    implementation(
        "com.squareup.okhttp3:okhttp:4.12.0"
    )

}
// test