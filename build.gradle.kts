plugins {
    id("com.android.application") version "8.6.1"
    id("org.jetbrains.kotlin.android") version "2.0.21"
    id("org.jetbrains.kotlin.plugin.compose") version "2.0.21"
}

android {
    namespace = "com.android.gamebar"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.android.gamebar.fork"
        minSdk = 33
        targetSdk = 35
        versionCode = 15500
        versionName = "15.5.0"
    }

    sourceSets {
        getByName("main") {
            manifest.srcFile("AndroidManifest.xml")
            java.srcDirs("src")
            res.srcDirs("res")
            assets.srcDirs("assets")
            aidl.srcDirs("src")
        }
    }

    buildFeatures {
        compose = true
        aidl = true
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions {
        jvmTarget = "17"
    }

    signingConfigs {
        getByName("debug") {
            enableV1Signing = true
            enableV2Signing = true
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            signingConfig = signingConfigs.getByName("debug")
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
        debug {
            isMinifyEnabled = false
        }
    }

    lint {
        abortOnError = false
        checkReleaseBuilds = false
    }
}

dependencies {
    compileOnly(files("libs/hidden-api.jar"))

    implementation("androidx.core:core-ktx:1.13.1")
    implementation("androidx.appcompat:appcompat:1.7.0")
    implementation("com.google.android.material:material:1.12.0")
    implementation("androidx.preference:preference-ktx:1.2.1")
    implementation("androidx.recyclerview:recyclerview:1.3.2")
    implementation("androidx.startup:startup-runtime:1.1.1")
    implementation("androidx.fragment:fragment-ktx:1.8.5")

    implementation(platform("androidx.compose:compose-bom:2024.10.01"))
    implementation("androidx.activity:activity-compose:1.9.3")
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.foundation:foundation")
    implementation("androidx.compose.animation:animation")
    implementation("androidx.compose.material:material-icons-extended")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.8.7")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.9.0")

    implementation("com.airbnb.android:lottie:6.6.0")
}

tasks.register<Copy>("copyApk") {
    dependsOn("assembleRelease")
    from("$projectDir/build/outputs/apk/release") {
        include("*-release.apk")
        rename { "GameBar.apk" }
    }
    into("$projectDir/out")
}

tasks.register<Zip>("packageModule") {
    dependsOn("assembleRelease", "copyApk")
    archiveFileName.set("GameBar-Module-v1.0.zip")
    destinationDirectory.set(file("$projectDir/out"))

    dirPermissions { unix("0755") }
    filePermissions { unix("0644") }

    from("$projectDir/module") {
        exclude("system/**")
        exclude("service.sh")
        exclude("customize.sh")
        exclude("META-INF/com/google/android/update-binary")
    }
    from("$projectDir/module/service.sh") {
        filePermissions { unix("0755") }
    }
    from("$projectDir/module/customize.sh") {
        filePermissions { unix("0755") }
    }
    from("$projectDir/module/META-INF/com/google/android/update-binary") {
        filePermissions { unix("0755") }
        into("META-INF/com/google/android")
    }
}
