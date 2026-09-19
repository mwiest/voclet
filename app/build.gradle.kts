import java.net.URI
import java.security.MessageDigest
import java.util.Properties
import java.util.zip.ZipFile

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.ksp)
    alias(libs.plugins.hilt)
    alias(libs.plugins.room)
    alias(libs.plugins.kotlin.serialization)
}

// Load keystore properties if available
val keystorePropertiesFile = project.file("keystore.properties")
val keystoreProperties = Properties()
if (keystorePropertiesFile.exists()) {
    keystoreProperties.load(keystorePropertiesFile.inputStream())
}

// ncnn publishes no Maven artifact, so the prebuilt Android release is
// fetched at build time and checked against its hash. Nothing binary in git.
val ncnnVersion = "20260526"
val ncnnSha256 = "85b18b875488585c2d21360430e0e54abb6c04aa88094b471c20208ab55ff796"
val ncnnRoot = layout.buildDirectory.dir("ncnn/ncnn-$ncnnVersion-android")

val fetchNcnn = tasks.register("fetchNcnn") {
    description = "Downloads the prebuilt ncnn the OCR JNI layer links against."
    outputs.dir(ncnnRoot)
    val zipFile = layout.buildDirectory.file("ncnn/ncnn-$ncnnVersion-android.zip")
    val into = layout.buildDirectory.dir("ncnn")
    doLast {
        val zip = zipFile.get().asFile
        if (!zip.exists()) {
            zip.parentFile.mkdirs()
            val url = "https://github.com/Tencent/ncnn/releases/download/" +
                "$ncnnVersion/ncnn-$ncnnVersion-android.zip"
            URI(url).toURL().openStream().use { source ->
                zip.outputStream().use { source.copyTo(it) }
            }
        }
        val digest = MessageDigest.getInstance("SHA-256")
        zip.inputStream().use { stream ->
            val buffer = ByteArray(1 shl 16)
            while (true) {
                val read = stream.read(buffer)
                if (read <= 0) break
                digest.update(buffer, 0, read)
            }
        }
        val got = digest.digest().joinToString("") { "%02x".format(it) }
        check(got == ncnnSha256) { "ncnn zip is $got, expected $ncnnSha256" }

        val target = into.get().asFile
        val archive = ZipFile(zip)
        try {
            for (entry in archive.entries().asSequence()) {
                val file = File(target, entry.name)
                // the archive is ours by hash, but a path check costs nothing
                check(file.canonicalPath.startsWith(target.canonicalPath)) {
                    "zip entry escapes the target: ${entry.name}"
                }
                if (entry.isDirectory) {
                    file.mkdirs()
                } else {
                    file.parentFile.mkdirs()
                    archive.getInputStream(entry).use { source ->
                        file.outputStream().use { source.copyTo(it) }
                    }
                }
            }
        } finally {
            archive.close()
        }
    }
}

tasks.named("preBuild") { dependsOn(fetchNcnn) }

android {
    namespace = "com.github.mwiest.voclet"
    compileSdk = 37

    signingConfigs {
        create("release") {
            if (keystorePropertiesFile.exists()) {
                storeFile = file(keystoreProperties["storeFile"] as String)
                storePassword = keystoreProperties["storePassword"] as String
                keyAlias = keystoreProperties["keyAlias"] as String
                keyPassword = keystoreProperties["keyPassword"] as String
            }
        }
    }

    defaultConfig {
        applicationId = "com.github.mwiest.voclet"
        minSdk = 28
        targetSdk = 37
        versionCode = 1
        versionName = "1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"

        // llama.cpp only ships these two, so 32-bit devices never had on-device
        // AI anyway; keeping the other ABIs cost 60 MiB of the universal APK.
        ndk { abiFilters += listOf("arm64-v8a", "x86_64") }

        externalNativeBuild {
            cmake {
                arguments += "-DNCNN_ROOT=${ncnnRoot.get().asFile.absolutePath}"
                cppFlags += "-std=c++17"
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
            if (keystorePropertiesFile.exists()) {
                signingConfig = signingConfigs.getByName("release")
            }
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_21
        targetCompatibility = JavaVersion.VERSION_21
    }
    buildFeatures {
        compose = true
        buildConfig = true
    }
    externalNativeBuild {
        cmake {
            path = file("src/main/cpp/CMakeLists.txt")
            version = "3.22.1"
        }
    }
    testOptions {
        unitTests {
            // Framework stubs return defaults instead of throwing, so code under
            // test can call android.util.Log. Only logging relies on this - the
            // logic under test stays pure (org.json and Bitmap are still absent
            // on the JVM, so parsers are hand-rolled).
            isReturnDefaultValues = true
        }
    }
}

room {
    schemaDirectory("$projectDir/schemas")
}

kotlin {
    jvmToolchain(21)
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.material)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.material3.adaptive)
    implementation(libs.androidx.compose.material.icons.extended)
    implementation(libs.androidx.core.splashscreen)
    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
    debugImplementation(libs.androidx.compose.ui.tooling)
    debugImplementation(libs.androidx.compose.ui.test.manifest)
    implementation(libs.androidx.room.runtime)
    implementation(libs.androidx.room.ktx)
    ksp(libs.androidx.room.compiler)
    implementation(libs.androidx.navigation.compose)
    implementation(libs.hilt.android)
    ksp(libs.hilt.compiler)
    implementation(libs.androidx.hilt.navigation.compose)
    implementation(libs.androidx.camera.core)
    implementation(libs.androidx.camera.camera2)
    implementation(libs.androidx.camera.lifecycle)
    implementation(libs.androidx.camera.view)
    implementation(libs.commons.csv)
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.androidx.work.runtime.ktx)
    implementation(libs.llamacpp.kotlin)
    implementation(libs.okhttp)
    testImplementation(libs.okhttp.mockwebserver)
}