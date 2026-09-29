plugins {
  alias(libs.plugins.android.library)
}

android {
  namespace = "com.google.ai.edge.gallery.modeldownload"
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
  implementation(libs.androidx.core.ktx)
  implementation(libs.androidx.lifecycle.runtime.ktx)
  implementation(libs.androidx.work.runtime)
  implementation(libs.com.google.code.gson)
  testImplementation(libs.junit)
}
