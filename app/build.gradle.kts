plugins {

    alias(libs.plugins.android.application)

    id("com.google.gms.google-services")

}

android {

    namespace =
        "com.fastnet.ebonefieldmanager"

    compileSdk = 34

    defaultConfig {

        applicationId =
            "com.fastnet.ebonefieldmanager"

        minSdk = 24

        targetSdk = 34

        versionCode = 1

        versionName = "1.0"

        testInstrumentationRunner =
            "androidx.test.runner.AndroidJUnitRunner"
    }

    buildTypes {

        release {

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

}
