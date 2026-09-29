plugins {
  alias(libs.plugins.android.library)
}

android {
  namespace = "com.xiaoma.db_lib"
  compileSdk = 37

  defaultConfig {
    minSdk = 31
    consumerProguardFiles("consumer-rules.pro")
    javaCompileOptions {
      annotationProcessorOptions {
        argument("room.schemaLocation", "$projectDir/schemas")
      }
    }
  }

  buildTypes {
    release {
      isMinifyEnabled = false
      proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
    }
  }

  buildFeatures { buildConfig = true }

  compileOptions {
    sourceCompatibility = JavaVersion.VERSION_11
    targetCompatibility = JavaVersion.VERSION_11
  }
}

dependencies {
  implementation(libs.androidx.core.ktx)
  implementation(libs.androidx.room.runtime)
  annotationProcessor(libs.androidx.room.compiler)
}
