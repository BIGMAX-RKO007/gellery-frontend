plugins {
  alias(libs.plugins.android.library)
  alias(libs.plugins.kotlin.compose)
}

android {
  namespace = "com.google.ai.edge.gallery.modelmanagerui"
  compileSdk { this.version = release(37) { minorApiLevel = 0 } }

  defaultConfig { minSdk = 31 }

  compileOptions {
    sourceCompatibility = JavaVersion.VERSION_11
    targetCompatibility = JavaVersion.VERSION_11
  }

  buildFeatures { compose = true }

  // Keep an offline fallback identical to the Gallery 1.0.19 catalog.
  sourceSets.named("main") { assets.srcDir("../../../model_allowlists") }
}

dependencies {
  api(project(":model-download"))
  implementation(libs.androidx.core.ktx)
  implementation(libs.androidx.activity.compose)
  implementation(libs.androidx.lifecycle.runtime.ktx)
  implementation(platform(libs.androidx.compose.bom))
  implementation(libs.androidx.ui)
  implementation(libs.androidx.ui.graphics)
  implementation(libs.androidx.ui.tooling.preview)
  implementation(libs.androidx.material3)
  implementation(libs.material.icon.extended)
  debugImplementation(libs.androidx.ui.tooling)
}
