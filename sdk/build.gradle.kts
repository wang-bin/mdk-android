plugins {
  alias(libs.plugins.androidLibrary)
  alias(libs.plugins.kotlin.android)
}

android {
  namespace = "com.mediadevkit.sdk"
  compileSdk = libs.versions.compileSdk.get().toInt()
  buildToolsVersion = libs.versions.buildTools.get()

  defaultConfig {
    minSdk = libs.versions.minSdk.get().toInt()
    consumerProguardFiles += file("consumer-rules.pro")
    externalNativeBuild {
      cmake {
        //cppFlags += ""
        arguments += "-DANDROID_STL=c++_shared"
      }
      ndkVersion = libs.versions.ndk.get()
    }
  }

  buildTypes {
    val release by getting {
      isMinifyEnabled = false
      isJniDebuggable = true
      proguardFiles += listOf(
        getDefaultProguardFile("proguard-android-optimize.txt"),
        file("proguard-rules.pro"),
      )
    }
  }

  externalNativeBuild {
    cmake {
      path = file("src/main/cpp/CMakeLists.txt")
      version = libs.versions.cmake.get()
    }
  }

  compileOptions {
    sourceCompatibility = JavaVersion.VERSION_11
    targetCompatibility = JavaVersion.VERSION_11
  }
  kotlinOptions {
    jvmTarget = "11"
  }


}

dependencies {
  api(libs.androidx.core)
  api(libs.kotlinx.coroutines.core)
  implementation(libs.androidx.core)
}

