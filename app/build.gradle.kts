import java.net.URI

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.ksp)
    alias(libs.plugins.hilt.android)
}

android {
    namespace = "com.antigravity.mobile"
    compileSdk = libs.versions.compileSdk.get().toInt()

    defaultConfig {
        applicationId = "com.antigravity.mobile"
        minSdk = libs.versions.minSdk.get().toInt()
        targetSdk = libs.versions.targetSdk.get().toInt()
        versionCode = 1
        versionName = "0.1.0-alpha"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    buildTypes {
        release {
            isMinifyEnabled = false
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

    kotlinOptions {
        jvmTarget = "17"
    }

    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1,INDEX.LIST,io.netty.versions.properties}"
        }
    }
}

dependencies {
    implementation(project(":core:contracts"))
    implementation(project(":core:chroot-runtime"))
    implementation(project(":core:account-manager"))
    implementation(project(":core:acp-client"))
    implementation(project(":core:root-control"))
    implementation(project(":core:accessibility-engine"))
    implementation(project(":core:android-mcp"))
    implementation(project(":feature:workspace-ui"))
    implementation(project(":feature:overlay-voice"))
    implementation(project(":feature:automation-security"))

    implementation(libs.androidx.core.ktx)
    implementation(libs.google.material)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.bundles.compose)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.navigation.compose)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.hilt.navigation.compose)
    implementation(libs.androidx.work.runtime.ktx)
    implementation(libs.androidx.hilt.work)
    ksp(libs.androidx.hilt.compiler)
    implementation(libs.hilt.android)
    ksp(libs.hilt.compiler)

    debugImplementation(libs.androidx.compose.ui.tooling)
    debugImplementation(libs.androidx.compose.ui.test.manifest)

    testImplementation(libs.bundles.unit.testing)
    androidTestImplementation(libs.androidx.test.ext.junit)
    androidTestImplementation(libs.androidx.test.espresso.core)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
}

/**
 * Opt-in Gradle task to download an ARM64 glibc rootfs tarball into
 * `app/src/main/assets/rootfs/arm64-rootfs.tar.gz` for offline APK bundling (Decision 2).
 * Override URL via `-ProotfsUrl=<url>`. Resulting `.tar.gz` is gitignored.
 */
tasks.register("fetchArm64Rootfs") {
    group = "antigravity"
    description = "Downloads the ARM64 glibc rootfs tarball into app/src/main/assets/rootfs/arm64-rootfs.tar.gz."
    doLast {
        val defaultUrl = "https://partner-images.canonical.com/oci/noble/current/ubuntu-noble-oci-arm64-root.tar.gz"
        val url = (project.findProperty("rootfsUrl") as String?) ?: defaultUrl
        val outFile = layout.projectDirectory.file("src/main/assets/rootfs/arm64-rootfs.tar.gz").asFile
        outFile.parentFile.mkdirs()
        logger.lifecycle("Downloading ARM64 rootfs from {} -> {}", url, outFile.absolutePath)
        URI(url).toURL().openStream().use { input ->
            outFile.outputStream().use { output ->
                input.copyTo(output)
            }
        }
        logger.lifecycle("Downloaded {} bytes to {}", outFile.length(), outFile.absolutePath)
    }
}
