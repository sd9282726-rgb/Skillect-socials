import java.util.Properties
import com.google.gms.googleservices.GoogleServicesPlugin.MissingGoogleServicesStrategy

plugins {
  alias(libs.plugins.android.application)
  alias(libs.plugins.kotlin.compose)
  alias(libs.plugins.kotlin.serialization)
  alias(libs.plugins.google.devtools.ksp)
  alias(libs.plugins.secrets)
  alias(libs.plugins.google.services)
  alias(libs.plugins.firebase.crashlytics)
  alias(libs.plugins.firebase.perf)
}

android {
  namespace = "com.example"
  compileSdk { version = release(36) { minorApiLevel = 1 } }

  defaultConfig {
    // Read client configuration from the ignored CI/local .env file, falling back
    // to documented placeholders so lint and unit tests can compile without secrets.
    val appEnv = Properties().apply {
      val envFile = rootProject.file(".env")
      val fallbackFile = rootProject.file(".env.example")
      val source = if (envFile.isFile) envFile else fallbackFile
      if (source.isFile) source.inputStream().use { load(it) }
    }
    fun String.asBuildConfigString(): String =
      "\"" + replace("\\\\", "\\\\\\\\")
        .replace("\"", "\\\"")
        .replace("\\n", "\\\\n") + "\""

    buildConfigField(
      "String",
      "SUPABASE_URL",
      appEnv.getProperty("SUPABASE_URL", "").asBuildConfigString(),
    )
    buildConfigField(
      "String",
      "SUPABASE_ANON_KEY",
      appEnv.getProperty("SUPABASE_ANON_KEY", "").asBuildConfigString(),
    )

    applicationId = "com.skillect.app"
    minSdk = 24
    targetSdk = 36
    versionCode = 37
    versionName = "2.2.5"

    testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
  }

  signingConfigs {
    create("release") {
      val signingFile = rootProject.file("keystore.properties")
      val signingProperties = Properties().apply {
        if (signingFile.exists()) {
          signingFile.inputStream().use { load(it) }
        }
      }
      val keystorePath = System.getenv("KEYSTORE_PATH")
        ?: signingProperties.getProperty("storeFile")
        ?: "my-upload-key.jks"
      storeFile = rootProject.file(keystorePath)
      storePassword = System.getenv("STORE_PASSWORD")
        ?: signingProperties.getProperty("storePassword")
      keyAlias = System.getenv("KEY_ALIAS")
        ?: signingProperties.getProperty("keyAlias")
        ?: "upload"
      keyPassword = System.getenv("KEY_PASSWORD")
        ?: signingProperties.getProperty("keyPassword")
    }
    create("debugConfig") {
      storeFile = file("${rootDir}/debug.keystore")
      storePassword = "android"
      keyAlias = "androiddebugkey"
      keyPassword = "android"
    }
  }

  buildTypes {
    release {
      isCrunchPngs = false
      isMinifyEnabled = false
      proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
      signingConfig = signingConfigs.getByName("release")
    }
    debug {
      signingConfig = signingConfigs.getByName("debugConfig")
    }
  }
  compileOptions {
    sourceCompatibility = JavaVersion.VERSION_11
    targetCompatibility = JavaVersion.VERSION_11
    isCoreLibraryDesugaringEnabled = true
  }
  buildFeatures {
    compose = true
    buildConfig = true
  }
  testOptions { unitTests { isIncludeAndroidResources = true } }
  dependenciesInfo {
    includeInApk = false
    includeInBundle = true
  }
}

secrets {
  propertiesFileName = ".env"
  defaultPropertiesFileName = ".env.example"
  ignoreList.add("KEYSTORE_PATH")
  ignoreList.add("KEY_ALIAS")
  ignoreList.add("STORE_PASSWORD")
  ignoreList.add("KEY_PASSWORD")
  ignoreList.add("SUPABASE_URL")
  ignoreList.add("SUPABASE_ANON_KEY")
}

googleServices { missingGoogleServicesStrategy = MissingGoogleServicesStrategy.IGNORE }

dependencies {
  coreLibraryDesugaring("com.android.tools:desugar_jdk_libs:2.1.4")
  implementation(libs.androidx.activity.compose)
  implementation(libs.androidx.fragment.ktx)
  implementation(platform(libs.androidx.compose.bom))
  implementation(libs.androidx.compose.material.icons.extended)
  implementation(libs.androidx.compose.material3)
  implementation(libs.androidx.compose.ui)
  implementation(libs.androidx.compose.ui.tooling.preview)
  implementation(libs.androidx.core.ktx)
  implementation(libs.androidx.splashscreen)
  implementation(libs.androidx.datastore.preferences)
  implementation(libs.androidx.lifecycle.runtime.compose)
  implementation(libs.androidx.lifecycle.runtime.ktx)
  implementation(libs.androidx.lifecycle.viewmodel.compose)
  implementation(libs.androidx.navigation.compose)
  implementation(libs.coil.compose)
  implementation(libs.converter.moshi)
  implementation(platform(libs.firebase.bom))
  implementation(libs.androidx.media3.exoplayer)
  implementation(libs.androidx.media3.ui)
  implementation(libs.firebase.messaging)
  implementation(libs.firebase.analytics)
  implementation(libs.firebase.crashlytics)
  implementation(libs.firebase.perf)
  implementation(libs.kotlinx.coroutines.android)
  implementation(libs.kotlinx.coroutines.core)
  implementation(libs.kotlinx.serialization.json)
  implementation(libs.logging.interceptor)
  implementation(libs.moshi.kotlin)
  implementation(libs.okhttp)
  implementation(libs.retrofit)
  testImplementation(libs.androidx.compose.ui.test.junit4)
  testImplementation(libs.androidx.core)
  testImplementation(libs.androidx.junit)
  testImplementation(libs.junit)
  testImplementation(libs.kotlinx.coroutines.test)
  testImplementation(libs.robolectric)
  androidTestImplementation(platform(libs.androidx.compose.bom))
  androidTestImplementation(libs.androidx.compose.ui.test.junit4)
  androidTestImplementation(libs.androidx.espresso.core)
  androidTestImplementation(libs.androidx.junit)
  androidTestImplementation(libs.androidx.runner)
  debugImplementation(libs.androidx.compose.ui.test.manifest)
  debugImplementation(libs.androidx.compose.ui.tooling)
  "ksp"(libs.moshi.kotlin.codegen)
}

tasks.configureEach {
  if (name == "preReleaseBuild") {
    doFirst {
      val configFile = rootProject.file(".env")
      check(configFile.isFile) {
        "Release build requires the ignored root .env file with SUPABASE_URL and SUPABASE_ANON_KEY."
      }
      val config = Properties().apply {
        configFile.inputStream().use { load(it) }
      }
      val supabaseUrl = config.getProperty("SUPABASE_URL").orEmpty()
      val supabaseKey = config.getProperty("SUPABASE_ANON_KEY").orEmpty()
      check(
        supabaseUrl.startsWith("https://") &&
          !supabaseUrl.contains("YOUR_PROJECT_REF") &&
          supabaseKey.isNotBlank() &&
          !supabaseKey.contains("YOUR_SUPABASE_ANON_KEY"),
      ) {
        "Release build requires valid SUPABASE_URL and SUPABASE_ANON_KEY values in .env."
      }
    }
  }
}
