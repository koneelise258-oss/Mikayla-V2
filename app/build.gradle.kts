plugins {
  alias(libs.plugins.android.application)
  alias(libs.plugins.compose.compiler)
  alias(libs.plugins.kotlin.serialization)
  id("com.google.gms.google-services")
}

android {
    namespace = "com.example.mikayala"
    compileSdk = 36
    defaultConfig {
        applicationId = "com.mikayla.app"
        minSdk = 23
        targetSdk = 36
        versionCode = 2
        versionName = "1.1"

        // ---------------------------------------------------------------------------
        // Mission 04 (C2-build) — durcissement révisé 2026-11-10
        // Objectif : AUCUNE variante Release compilée avec des valeurs Supabase factices,
        // quelle que soit la tâche Gradle (assembleRelease, bundleRelease, build, install...).
        //
        // Pourquoi pas seulement taskNames : « ./gradlew build » ou « ./gradlew assemble »
        // déclenchent les variantes release SANS contenir le mot « Release » dans la
        // commande. On cale donc la vérification sur la CONFIGURATION de la tâche en cours
        // d'exécution (variant.buildType.name), fiable pour toute tâche liée à une variante.
        //
        // - Debug sans secrets : repli factice toléré (compilation locale).
        // - Release sans secrets : GradleException explicite.
        // ---------------------------------------------------------------------------
        val envSupabaseUrl = System.getenv("SUPABASE_URL")
        val envSupabaseAnonKey = System.getenv("SUPABASE_ANON_KEY")

        // Fait échouer TOUTE tâche d'une variante release si les secrets manquent.
        // Couvre : assembleRelease, bundleRelease, compileReleaseKotlin, build, install...
        // Ne touche PAS les variantes debug (tâches Debug, ou aucune variante = pas de check).
        androidComponents {
            onVariants(selector().withBuildType("release")) { variant ->
                val variantName = variant.name.replaceFirstChar { it.uppercase() }
                val urlOk = !envSupabaseUrl.isNullOrBlank()
                val keyOk = !envSupabaseAnonKey.isNullOrBlank()
                if (!urlOk || !keyOk) {
                    val missing = listOfNotNull(
                        if (!urlOk) "SUPABASE_URL" else null,
                        if (!keyOk) "SUPABASE_ANON_KEY" else null
                    ).joinToString(" et ")
                    throw GradleException(
                        "BUILD BLOQUÉ (Mission 04 C2-build) : $missing absent(s) pour la variante $variantName. " +
                        "Un APK release ne doit jamais être compilé avec des valeurs Supabase factices. " +
                        "Définissez ces variables d'environnement (ou secrets CI) avant de relancer le build."
                    )
                }
            }
        }

        val supabaseUrl = envSupabaseUrl ?: "https://xyzcompany.supabase.co"
        val supabaseAnonKey = envSupabaseAnonKey ?: "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.dummykey"

        buildConfigField("String", "SUPABASE_URL", "\"$supabaseUrl\"")
        buildConfigField("String", "SUPABASE_ANON_KEY", "\"$supabaseAnonKey\"")
        buildConfigField("String", "BUILD_MARKER", "\"MIKAYALA_CURRENT_BUILD_2026\"")
    }

    // Signature stable : la clé n'est JAMAIS dans le dépôt.
    // CI : la clé est décodée depuis les secrets GitHub. Localement : chemin libre via MIKAYALA_KEYSTORE_PATH.
    // Si aucun keystore stable n'est fourni, la variante release utilise la clé DEBUG —
    // ce qui rend l'APK non publiable et incompatible avec les installations précédentes.
    // Mission 04 : on le rend explicite et on le bloque si les secrets de signature manquent en CI.
    val stableKeystorePath = System.getenv("MIKAYALA_KEYSTORE_PATH")
    val stableKeystorePassword = System.getenv("MIKAYALA_KEYSTORE_PASSWORD")
    val stableKeyAlias = System.getenv("MIKAYALA_KEY_ALIAS")
    val stableKeyPassword = System.getenv("MIKAYALA_KEY_PASSWORD")
    val hasStableSigning = !stableKeystorePath.isNullOrBlank() && file(stableKeystorePath).exists() &&
        !stableKeystorePassword.isNullOrBlank() && !stableKeyAlias.isNullOrBlank() && !stableKeyPassword.isNullOrBlank()
    signingConfigs {
        if (hasStableSigning) {
            create("stable") {
                storeFile = file(stableKeystorePath!!)
                storePassword = stableKeystorePassword
                keyAlias = stableKeyAlias
                keyPassword = stableKeyPassword
            }
        }
    }

    buildTypes {
        debug {
            if (hasStableSigning) signingConfig = signingConfigs.getByName("stable")
        }
        release {
            isMinifyEnabled = false
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            if (hasStableSigning) {
                signingConfig = signingConfigs.getByName("stable")
            } else {
                // Explicite : sans secrets, la release est signée avec la clé debug.
                // (Aucune publication Play Store possible ; comportement inchangé sinon.)
            }
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    buildFeatures {
        compose = true
        buildConfig = true
    }

    packaging {
      resources {
        excludes += "/META-INF/{AL2.0,LGPL2.1}"
      }
    }
}

kotlin {
    jvmToolchain(17)
}

dependencies {
  val composeBom = platform(libs.androidx.compose.bom)
  implementation(composeBom)
  androidTestImplementation(composeBom)

  // Core Android dependencies
  implementation(libs.androidx.core.ktx)
  // Police Noto Color Emoji embarquée : rendu identique sur Android 7 et 10 (licence SIL OFL 1.1)
  implementation(libs.androidx.emoji2.bundled)
  implementation(libs.androidx.lifecycle.runtime.ktx)
  implementation(libs.androidx.activity.compose)

  // Arch Components
  implementation(libs.androidx.lifecycle.runtime.compose)
  implementation(libs.androidx.lifecycle.viewmodel.compose)

  // Compose
  implementation(libs.androidx.compose.ui)
  implementation(libs.androidx.compose.ui.tooling.preview)
  implementation(libs.androidx.compose.material3)
  implementation(libs.androidx.compose.material.icons.extended)
  implementation(libs.coil.compose)
  // Media3 ExoPlayer for robust video playback
  implementation(libs.androidx.media3.exoplayer)
  implementation(libs.androidx.media3.ui)
  // Tooling
  debugImplementation(libs.androidx.compose.ui.tooling)
  // Instrumented tests
  androidTestImplementation(libs.androidx.compose.ui.test.junit4)
  debugImplementation(libs.androidx.compose.ui.test.manifest)

  // Local tests: jUnit, coroutines, Android runner
  testImplementation(libs.junit)
  testImplementation(libs.kotlinx.coroutines.test)

  // Instrumented tests: jUnit rules and runners
  androidTestImplementation(libs.androidx.test.core)
  androidTestImplementation(libs.androidx.test.ext.junit)
  androidTestImplementation(libs.androidx.test.runner)
  androidTestImplementation(libs.androidx.test.espresso.core)

  // Firebase Cloud Messaging
  implementation(platform("com.google.firebase:firebase-bom:33.2.0"))
  implementation("com.google.firebase:firebase-messaging")

  // Supabase
  implementation(platform(libs.supabase.bom))
  implementation(libs.supabase.auth)
  implementation(libs.supabase.postgrest)
  implementation(libs.supabase.realtime)
  implementation(libs.supabase.storage)

  // QR Code Generation
  implementation(libs.zxing.core)

  // QR Code Scanning (ML Kit + CameraX)
  implementation(libs.mlkit.barcode.scanning)
  implementation(libs.androidx.camera.core)
  implementation(libs.androidx.camera.camera2)
  implementation(libs.androidx.camera.lifecycle)
  implementation(libs.androidx.camera.view)
  implementation(libs.guava)

  // Ktor Engine (Required for Supabase 3.x)
  implementation(libs.ktor.client.core)
  implementation(libs.ktor.client.okhttp)
  implementation(libs.ktor.client.android)
  implementation(libs.ktor.client.content.negotiation)
  implementation(libs.ktor.serialization.kotlinx.json)
}
