import java.util.Properties

plugins {
    id("com.android.application")
    // AGP 9 integra Kotlin: no se aplica ningún plugin de Kotlin aparte.
}

// Credenciales de firma del APK de release. Se leen de android/keystore.properties,
// que NO se versiona (ver .gitignore). Si el archivo no existe, el build de
// release queda sin firmar en lugar de fallar.
val keystorePropsFile = rootProject.file("keystore.properties")
val keystoreProps = Properties().apply {
    if (keystorePropsFile.exists()) keystorePropsFile.inputStream().use { load(it) }
}
val hayFirmaRelease = keystorePropsFile.exists() &&
    rootProject.file(keystoreProps.getProperty("RELEASE_STORE_FILE", "")).exists()

android {
    namespace = "com.brigada.confronta"
    compileSdk = 37

    defaultConfig {
        applicationId = "com.brigada.confronta"
        minSdk = 26
        targetSdk = 34   // evita el edge-to-edge forzado (35+) para que el contenido no quede bajo la barra de estado
        versionCode = 1
        versionName = "1.0"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    signingConfigs {
        if (hayFirmaRelease) {
            create("release") {
                storeFile = rootProject.file(keystoreProps.getProperty("RELEASE_STORE_FILE"))
                storePassword = keystoreProps.getProperty("RELEASE_STORE_PASSWORD")
                keyAlias = keystoreProps.getProperty("RELEASE_KEY_ALIAS")
                keyPassword = keystoreProps.getProperty("RELEASE_KEY_PASSWORD")
                enableV1Signing = true   // APK Signature Scheme v1 (jar)
                enableV2Signing = true   // v2 (full-file, Android 7+)
                enableV3Signing = true   // v3 (rotacion de clave, Android 9+)
            }
        }
    }

    buildTypes {
        release {
            if (hayFirmaRelease) signingConfig = signingConfigs.getByName("release")
            // Se deja sin ofuscar: R8 elimina por reflexion los data class de
            // Modelos.kt que Gson necesita, y no hay forma de probarlo en un
            // dispositivo antes de la defensa. Activarlo exige reglas -keep
            // para Gson/Retrofit y una prueba real en el celular.
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_1_8
        targetCompatibility = JavaVersion.VERSION_1_8
    }
    buildFeatures {
        viewBinding = true
        buildConfig = true   // habilita BuildConfig.DEBUG (usado en ApiClient)
    }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.appcompat)
    implementation(libs.material)
    implementation(libs.androidx.activity.ktx)
    implementation(libs.androidx.constraintlayout)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.retrofit)
    implementation(libs.retrofit.gson)
    implementation(libs.okhttp.logging)
    implementation(libs.zxing.core)
    implementation(libs.zxing.android)
    implementation(libs.androidx.biometric)
    implementation(libs.androidx.security.crypto)
    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
}
