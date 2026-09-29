plugins {
  alias(libs.plugins.android.library)
}

android {
  namespace = "com.google.ai.edge.gallery.aicore"
  compileSdk = 37

  defaultConfig { minSdk = 31 }

  compileOptions {
    sourceCompatibility = JavaVersion.VERSION_11
    targetCompatibility = JavaVersion.VERSION_11
  }
}

dependencies {
  api(libs.kotlinx.coroutines.core)
  implementation(libs.kotlinx.coroutines.android)
  implementation(libs.litertlm)
  testImplementation(libs.junit)
}
