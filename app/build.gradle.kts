plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("kotlin-kapt")
    id("com.google.dagger.hilt.android")
}

android {
    namespace = "com.secureguard.mdm"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.secureguard.mdm"
        minSdk = 22
        targetSdk = 34
        versionCode = 1
        versionName = "0.4.5"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        vectorDrawables {
            useSupportLibrary = true
        }
    }

    buildTypes {
        getByName("release") {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
        getByName("debug") {
            isMinifyEnabled = false
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions {
        jvmTarget = JavaVersion.VERSION_17.toString()
    }

    // משאיר את viewBinding פעיל כי הוא משמש במסכי ה-XML שנותרו (כמו Main Activity)
    buildFeatures {
        compose = true
        viewBinding = true
    }
    composeOptions {
        kotlinCompilerExtensionVersion = "1.5.8"
    }
    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }

    // שינוי שם הקובץ ל-Abloq-(סוג הבילד).apk
    applicationVariants.all {
        outputs.all {
            val apkName = "Abloq-${buildType.name}.apk"
            (this as com.android.build.gradle.internal.api.BaseVariantOutputImpl).outputFileName = apkName
        }
    }
}

dependencies {
    val roomVersion = "2.6.1"

    // Kotlin/Android Core
    implementation("androidx.core:core-ktx:1.12.0")

    // Room Database
    implementation("androidx.room:room-runtime:$roomVersion")
    implementation("androidx.room:room-ktx:$roomVersion")
    kapt("androidx.room:room-compiler:$roomVersion")

    // General Android Views (AppCompat, ConstraintLayout) - נשאר לתמיכה במסכים ישנים/דיאלוגים
    // שים לב: ה-Material dependency הופיע פעמיים בגרסה הקודמת. השארתי רק אחת.
    implementation("com.google.android.material:material:1.11.0")
    implementation("androidx.appcompat:appcompat:1.6.1")
    implementation("androidx.constraintlayout:constraintlayout:2.1.4")

    // ========== Jetpack Compose & UI ==========

    // Compose Bill of Materials (BOM) - מנהל את גרסאות הקומפוז עבורך
    // נשאר בגרסה הקיימת שלך: 2024.02.01
    implementation(platform("androidx.compose:compose-bom:2024.02.01"))
    androidTestImplementation(platform("androidx.compose:compose-bom:2024.02.01"))

    // Compose Core Dependencies
    implementation("androidx.activity:activity-compose:1.8.2")
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-graphics")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended") // עבור אייקונים מורחבים

    // Compose Tooling & Testing
    implementation("androidx.compose.ui:ui-tooling-preview")
    debugImplementation("androidx.compose.ui:ui-tooling")
    debugImplementation("androidx.compose.ui:ui-test-manifest")

    // ========== Lifecycle & Navigation ==========

    // Lifecycle (עבור ON_RESUME, ON_PAUSE ו-DisposableEffect)
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.7.0")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.7.0") // Runtime Compose

    // ViewModel integration for Compose (עבור hiltViewModel() ו-collectAsStateWithLifecycle)
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.7.0")

    // Navigation (Compose)
    implementation("androidx.navigation:navigation-compose:2.7.7")
    implementation("androidx.hilt:hilt-navigation-compose:1.2.0")

    // ========== Hilt & Utilities ==========

    // Hilt
    implementation("com.google.dagger:hilt-android:2.50")
    kapt("com.google.dagger:hilt-compiler:2.50")

    // JSON Serialization
    implementation("com.google.code.gson:gson:2.10.1")

    // Security
    implementation("at.favre.lib:bcrypt:0.10.2")

    // Accompanist (עבור ציור Drawables ב-Compose)
    implementation("com.google.accompanist:accompanist-drawablepainter:0.32.0")

    // Testing
    testImplementation("junit:junit:4.13.2")
    androidTestImplementation("androidx.test.ext:junit:1.1.5")
    androidTestImplementation("androidx.test.espresso:espresso-core:3.5.1")
    androidTestImplementation("androidx.compose.ui:ui-test-junit4")
}

kapt {
    correctErrorTypes = true
}