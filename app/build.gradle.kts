import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.ksp)
}

android {
    namespace = "com.example.inventory"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.example.inventory"
        minSdk = 26
        targetSdk = 34
        versionCode = 1
        versionName = "1.0"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        vectorDrawables {
            useSupportLibrary = true
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
        }
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlin {
        compilerOptions {
            jvmTarget.set(JvmTarget.JVM_17)
        }
    }

    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
            pickFirsts += "META-INF/io.netty.versions.properties"
            pickFirsts += "META-INF/INDEX.LIST"
            pickFirsts += "META-INF/DEPENDENCIES"
        }
    }

    lint {
        lintConfig = file("lint.xml")
    }
}

ksp {
    arg("room.schemaLocation", "$projectDir/schemas")
}

dependencies {
    // Compose BOM
    implementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(platform(libs.androidx.compose.bom))
    
    // AndroidX Core
    implementation(libs.androidx.core.ktx)
    implementation(libs.bundles.lifecycle)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.appcompat)
    
    // Compose
    implementation(libs.bundles.compose)
    implementation(libs.androidx.navigation.compose)
    
    // Material Design
    implementation(libs.material)
    
    // Icons (Material Extended + Phosphor)
    implementation(libs.bundles.icons)
    
    // CameraX
    implementation(libs.bundles.camerax)
    
    // Security
    implementation(libs.androidx.security.crypto)

    // DataStore
    implementation(libs.androidx.datastore.preferences)

    // Room Database
    implementation(libs.bundles.room)
    implementation("androidx.room:room-paging:2.6.1")
    ksp(libs.androidx.room.compiler)


    // Paging
    implementation("androidx.paging:paging-runtime-ktx:3.3.0")
    implementation("androidx.paging:paging-compose:3.3.0")

    // Network
    implementation(libs.bundles.network)

    // AWS
    implementation(libs.bundles.aws)

    // File Processing
    implementation(libs.bundles.file.processing)

    implementation("com.huaban:jieba-analysis:1.0.2")

    // PaddleLite (使用 AAR 方式)
    implementation(files("libs/PaddleLite-android-armv8.aar"))


    // Testing
    testImplementation(libs.bundles.testing)
    testImplementation(libs.robolectric)
    androidTestImplementation(libs.bundles.android.testing)
    
    // Debug
    debugImplementation(libs.bundles.debug)
}
