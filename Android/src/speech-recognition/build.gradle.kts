plugins {
  alias(libs.plugins.android.library)
}

android {
  namespace = "com.example.voxmate.speech"
  compileSdk = 37

  defaultConfig {
    minSdk = 31
    ndk { abiFilters += listOf("arm64-v8a", "x86_64") }
  }

  compileOptions {
    sourceCompatibility = JavaVersion.VERSION_11
    targetCompatibility = JavaVersion.VERSION_11
  }

  androidResources {
    noCompress += listOf("onnx", "txt")
  }
}

dependencies {
  api(libs.kotlinx.coroutines.core)
  implementation(libs.kotlinx.coroutines.android)
  implementation(libs.sherpa.onnx)
  testImplementation(libs.junit)
}
