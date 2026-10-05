import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
  alias(libs.plugins.android.library)
  alias(libs.plugins.ksp)
}

android {
  namespace = "com.example.voxmate.persona"
  compileSdk { this.version = release(37) { minorApiLevel = 0 } }

  defaultConfig {
    minSdk = 31
    consumerProguardFiles("consumer-rules.pro")
  }

  buildTypes {
    release {
      isMinifyEnabled = false
      proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
    }
  }

  compileOptions {
    sourceCompatibility = JavaVersion.VERSION_11
    targetCompatibility = JavaVersion.VERSION_11
  }
}

kotlin { compilerOptions { jvmTarget.set(JvmTarget.JVM_11) } }

ksp {
  arg("room.schemaLocation", "$projectDir/schemas")
  arg("room.incremental", "true")
}

dependencies {
  implementation(libs.androidx.core.ktx)
  implementation(libs.androidx.room.persona.runtime)
  implementation(libs.androidx.room.persona.ktx)
  implementation(libs.com.google.code.gson)
  implementation(libs.kotlinx.coroutines.core)
  ksp(libs.androidx.room.persona.compiler)

  testImplementation(libs.junit)
}
