plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")

    id("org.jetbrains.kotlin.kapt")

    id("com.google.dagger.hilt.android")
    id("com.google.devtools.ksp")

    id("org.jetbrains.kotlin.plugin.serialization") // optional; useful for import/export later
    id("org.jetbrains.kotlin.plugin.compose")
}

android {
    namespace = "com.example.adobongkangkong"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.example.adobongkangkong"
        minSdk = 26
        targetSdk = 35
        versionCode = 1
        versionName = "0.1.0"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"

        buildConfigField("String", "USDA_API_KEY", "\"NW8f6sDOwtWk6EOjKZLefMR6wO3JSX8KkcRDHBUg\"")
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions {
        jvmTarget = "17"
        freeCompilerArgs += listOf(
            "-opt-in=kotlinx.coroutines.ExperimentalCoroutinesApi",
            "-opt-in=kotlinx.coroutines.FlowPreview"
        )
    }

    packaging {
        resources.excludes += "/META-INF/{AL2.0,LGPL2.1}"
    }
}

tasks.withType<Test>().configureEach {
    maxParallelForks = Runtime.getRuntime().availableProcessors() / 2
}

kotlin {
    jvmToolchain(17)
}

kapt {
    correctErrorTypes = true
}

ksp {
    arg("room.schemaLocation", "$projectDir/schemas")
}

dependencies {
    implementation(libs.androidx.material3)
    testImplementation(libs.junit.jupiter)

    // Compose BOM
    val composeBom = platform("androidx.compose:compose-bom:2024.10.00")
    implementation(composeBom)
    androidTestImplementation(composeBom)

    // Compose
    implementation("androidx.activity:activity-compose:1.9.3")
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-tooling-preview")
    debugImplementation("androidx.compose.ui:ui-tooling")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.navigation:navigation-compose:2.8.3")

    // Lifecycle
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.8.7")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.8.7")

    // Coroutines
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.9.0")

    // Room (KSP)
    implementation("androidx.room:room-runtime:2.6.1")
    implementation("androidx.room:room-ktx:2.6.1")
    ksp("androidx.room:room-compiler:2.6.1")

    // Hilt (KAPT)
    implementation("com.google.dagger:hilt-android:2.52")
    kapt("com.google.dagger:hilt-compiler:2.52")
    implementation("androidx.hilt:hilt-navigation-compose:1.2.0")

    // Serialization (optional)
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.7.3")

    // Unit testing
    testImplementation("junit:junit:4.13.2")
    testImplementation("org.jetbrains.kotlin:kotlin-test")

    // Android instrumented testing
    androidTestImplementation("androidx.test.ext:junit:1.1.5")
    androidTestImplementation("androidx.test.espresso:espresso-core:3.5.1")

    // Datastore
    implementation("androidx.datastore:datastore-preferences:1.1.1")

    // Camera
    implementation("androidx.camera:camera-core:1.3.4")
    implementation("androidx.camera:camera-camera2:1.3.4")
    implementation("androidx.camera:camera-lifecycle:1.3.4")
    implementation("androidx.camera:camera-view:1.3.4")
    implementation("androidx.exifinterface:exifinterface:1.3.7")
    implementation("com.google.mlkit:barcode-scanning:17.3.0")

    // Retrofit
    implementation("com.squareup.retrofit2:retrofit:2.11.0")
    implementation("com.squareup.retrofit2:converter-scalars:2.11.0")
    implementation("com.squareup.okhttp3:logging-interceptor:4.12.0")

    // Core WorkManager
    implementation("androidx.work:work-runtime-ktx:2.9.0")

    // Hilt + WorkManager integration (KAPT)
    implementation("androidx.hilt:hilt-work:1.2.0")
    kapt("androidx.hilt:hilt-compiler:1.2.0")
}

