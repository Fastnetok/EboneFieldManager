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

        versionCode = 22

        versionName = "1.0.22"

        testInstrumentationRunner =
            "androidx.test.runner.AndroidJUnitRunner"
    }

    buildTypes {

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
    implementation("androidx.biometric:biometric:1.1.0")

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

    implementation(
        "com.google.firebase:firebase-database-ktx:21.0.0"
    )

    implementation(
        "com.google.firebase:firebase-messaging-ktx:24.0.1"
    )

    implementation(
        "com.google.firebase:firebase-auth-ktx:22.3.1"
    )

    implementation(
        "com.squareup.okhttp3:okhttp:4.12.0"
    )

    androidTestImplementation(
        "androidx.test.ext:junit:1.1.5"
    )

    androidTestImplementation(
        "androidx.test.espresso:espresso-core:3.5.1"
    )

    testImplementation(
        "junit:junit:4.13.2"
    )

}