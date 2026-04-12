import java.util.Properties

val secretProperties = Properties().apply {
    val secretsFile = rootProject.file("secrets.properties")
    if (secretsFile.exists()) {
        secretsFile.inputStream().use { load(it) }
    }
}

fun secretOrEnv(name: String, defaultValue: String = ""): String =
    (secretProperties.getProperty(name) ?: System.getenv(name) ?: defaultValue).trim()

fun buildConfigString(name: String, defaultValue: String = ""): String {
    val value = secretOrEnv(name, defaultValue)
        .replace("\\", "\\\\")
        .replace("\"", "\\\"")
    return "\"$value\""
}

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.google.services)
}

android {
    namespace = "com.example.appbangiay"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.example.appbangiay"
        minSdk = 26
        targetSdk = 36
        versionCode = 4
        versionName = "2.1.1"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"

        buildConfigField("String", "GROQ_API_KEYS_RAW", buildConfigString("GROQ_API_KEYS_RAW"))
        buildConfigField("String", "MOMO_PARTNER_CODE", buildConfigString("MOMO_PARTNER_CODE", "MOMO"))
        buildConfigField("String", "MOMO_ACCESS_KEY", buildConfigString("MOMO_ACCESS_KEY"))
        buildConfigField("String", "MOMO_SECRET_KEY", buildConfigString("MOMO_SECRET_KEY"))
        buildConfigField(
            "String",
            "MOMO_API_ENDPOINT",
            buildConfigString("MOMO_API_ENDPOINT", "https://test-payment.momo.vn/v2/gateway/api/create")
        )
        buildConfigField(
            "String",
            "MOMO_REDIRECT_URL",
            buildConfigString("MOMO_REDIRECT_URL", "appbangiay://momo-return")
        )
        buildConfigField(
            "String",
            "MOMO_IPN_URL",
            buildConfigString("MOMO_IPN_URL", "https://example.com/ipn")
        )
        buildConfigField("String", "VNPAY_TMN_CODE", buildConfigString("VNPAY_TMN_CODE"))
        buildConfigField("String", "VNPAY_HASH_SECRET", buildConfigString("VNPAY_HASH_SECRET"))
    }

    buildFeatures {
        buildConfig = true
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
}

dependencies {
    implementation(libs.appcompat)
    implementation(libs.material)
    implementation(libs.activity)
    implementation(libs.constraintlayout)
    implementation(libs.viewpager2)
    implementation(libs.play.services.location)

    // Firebase
    implementation(platform(libs.firebase.bom))
    implementation(libs.firebase.auth)
    implementation(libs.firebase.firestore)
    implementation(libs.firebase.analytics)

    // Glide (GIF support)
    implementation("com.github.bumptech.glide:glide:4.16.0")
    annotationProcessor("com.github.bumptech.glide:compiler:4.16.0")

    // Google Sign-In (Credential Manager + Legacy fallback)
    implementation(libs.credentials)
    implementation(libs.credentials.play)
    implementation(libs.googleid)
    implementation("com.google.android.gms:play-services-auth:21.3.0")

    // OSMDroid — OpenStreetMap (no API key required)
    implementation("org.osmdroid:osmdroid-android:6.1.18")

    // CustomTabs — mở URL trong app (tránh crash khi không có browser ngoài)
    implementation("androidx.browser:browser:1.8.0")


    testImplementation(libs.junit)
    androidTestImplementation(libs.ext.junit)
    androidTestImplementation(libs.espresso.core)
}
