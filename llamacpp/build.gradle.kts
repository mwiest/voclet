plugins {
    alias(libs.plugins.android.library)
}

// Upstream's own module file applies maven-publish and GPG signing, so this one builds the
// submodule's sources instead.
val upstream = "../third_party/kotlinllamacpp/llamaCpp"

android {
    namespace = "org.nehuatl.llamacpp"
    compileSdk = 37
    ndkVersion = "28.2.13676358"

    defaultConfig {
        minSdk = 28
        consumerProguardFiles("consumer-rules.pro")
        externalNativeBuild {
            cmake {
                arguments += listOf("-DLLAMA_BUILD_COMMON=ON", "-DCMAKE_BUILD_TYPE=Release")
                // Unbounded ninja runs out of RAM on a 16 GB machine; the -flto links are the peak.
                arguments += listOf(
                    "-DCMAKE_JOB_POOLS=compile=4;link=1",
                    "-DCMAKE_JOB_POOL_COMPILE=compile",
                    "-DCMAKE_JOB_POOL_LINK=link",
                )
            }
        }
    }

    // The app's abiFilters only pick what gets packaged; without these CMake builds every ABI.
    buildTypes {
        debug {
            externalNativeBuild { cmake { abiFilters += listOf("arm64-v8a", "x86_64") } }
        }
        release {
            externalNativeBuild { cmake { abiFilters += listOf("arm64-v8a") } }
        }
    }

    sourceSets {
        getByName("main") {
            manifest.srcFile("$upstream/src/main/AndroidManifest.xml")
            kotlin.directories.add("$upstream/src/main/java")
        }
    }

    externalNativeBuild {
        cmake {
            path = file("$upstream/src/main/cpp/CMakeLists.txt")
            version = "3.22.1"
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_21
        targetCompatibility = JavaVersion.VERSION_21
    }
}

kotlin {
    jvmToolchain(21)
}

dependencies {
    implementation(libs.kotlinx.coroutines.android)
}
