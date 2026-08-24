plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.google.services)
}

android {
    namespace = "in.pcncloud.hotel"
    compileSdk {
        version = release(36) {
            minorApiLevel = 1
        }
    }

    signingConfigs {
        create("release") {
            storeFile = file("../pcncloud-release.jks")
            storePassword = "Superman@007"
            keyAlias = "pcncloud-key"
            keyPassword = "Superman@007"
        }
    }

    defaultConfig {
        applicationId = "in.pcncloud.hotel"
        minSdk = 24
        targetSdk = 36
        versionCode = 1
        versionName = "1.0"

        // Multi-tenant defaults — hotel id is paired at runtime via PairingActivity
        buildConfigField("String", "DEFAULT_ROOM_NUMBER", "\"101\"")
        // Staff Master PIN for TV Admin Mode (in-memory session only; change per hotel build if needed)
        buildConfigField("String", "DEFAULT_MASTER_PIN", "\"1234\"")

        vectorDrawables {
            useSupportLibrary = true
        }
    }

    flavorDimensions += "client"
    productFlavors {
        create("hotel") {
            dimension = "client"
            applicationId = "in.pcncloud.hotel"
            manifestPlaceholders["appName"] = "PCN CLOUD"
            buildConfigField("boolean", "IS_CORPORATE", "false")
        }
        create("corporate") {
            dimension = "client"
            applicationId = "in.pcncloud.corporate"
            manifestPlaceholders["appName"] = "L&T Training Hub"
            buildConfigField("boolean", "IS_CORPORATE", "true")
        }
    }

    buildTypes {
        debug {
            // Same cert as release so USB / hotelDebug sideload is not treated as an
            // unknown developer by Play Protect on the physical TV.
            signingConfig = signingConfigs.getByName("release")
        }
        release {
            signingConfig = signingConfigs.getByName("release")
            optimization {
                enable = false
            }
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
    buildFeatures {
        compose = true
        buildConfig = true
    }
}

dependencies {
    implementation(platform(libs.androidx.compose.bom))
    implementation(platform(libs.firebase.bom))

    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.appcompat)
    implementation(libs.androidx.cardview)
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.core.splashscreen)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.process)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.navigation.compose)
    implementation(libs.androidx.tv.foundation)
    implementation(libs.androidx.tv.material)
    implementation(libs.coil.compose)
    implementation(libs.coil)
    implementation(libs.coil.svg)
    implementation(libs.firebase.firestore)
    implementation(libs.firebase.database)
    implementation("com.google.firebase:firebase-config-ktx")
    implementation(libs.androidx.media3.exoplayer)
    implementation(libs.androidx.media3.ui)
    implementation(libs.androidx.media3.datasource)
    implementation(libs.androidx.media3.datasource.okhttp)
    implementation(libs.okhttp)

    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
    debugImplementation(libs.androidx.compose.ui.test.manifest)
    debugImplementation(libs.androidx.compose.ui.tooling)
}
