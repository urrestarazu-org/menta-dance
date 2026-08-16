plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.ksp)
    alias(libs.plugins.hilt.android)
}

android {
    namespace = "com.menta.android"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.menta.android"
        minSdk = 24
        targetSdk = 36
        versionCode = 1
        versionName = "0.1.0"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    buildTypes {
        // Android es cliente directo del API (ADR-0035): no pasa por el BFF.
        // Las rutas /api/v1/auth/* las expone el API en 8081; 8080 es el BFF y
        // sólo sirve el formulario web.
        debug {
            // 10.0.2.2 es la dirección con la que el emulador alcanza el host.
            // El cleartext que esto implica queda habilitado únicamente para
            // debug, vía src/debug/res/xml/network_security_config.xml.
            buildConfigField("String", "AUTH_API_BASE_URL", "\"http://10.0.2.2:8081\"")
        }

        release {
            // HTTPS obligatorio: release no habilita cleartext en ninguna forma.
            buildConfigField("String", "AUTH_API_BASE_URL", "\"https://api.mentadance.com\"")
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro",
            )
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_21
        targetCompatibility = JavaVersion.VERSION_21
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }
}

kotlin {
    // Fija el target de Kotlin en 21, igual que compileOptions arriba, sin
    // depender del JDK que lance el daemon de Gradle. Sin esto, Android
    // Studio (que por defecto usa su propio runtime como Gradle JVM, JDK 25
    // en Quail 3) hace que Kotlin caiga a targetear bytecode 24 mientras Java
    // sigue targeteando 21 segun compileOptions, y el propio Kotlin Gradle
    // Plugin bloquea la build por esa inconsistencia.
    jvmToolchain(21)
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.activity.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.foundation)
    implementation(libs.androidx.compose.runtime)
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.ktx)
    implementation(libs.hilt.android)

    ksp(libs.hilt.compiler)

    debugImplementation(libs.androidx.compose.ui.tooling)

    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.test.core)
    androidTestImplementation(libs.androidx.test.ext.junit)
    // Provee androidx.test.runner.AndroidJUnitRunner, declarado arriba como
    // testInstrumentationRunner. Sin esta dependencia la instrumentación no
    // arranca: ClassNotFoundException antes de ejecutar un solo test.
    androidTestImplementation(libs.androidx.test.runner)
}
