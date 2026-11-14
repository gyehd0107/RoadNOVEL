import java.util.Properties

// local.properties (커밋 안 됨)에서 비밀값·개인 설정 읽기
val localProps = Properties().apply {
    rootProject.file("local.properties").takeIf { it.exists() }?.inputStream()?.use { load(it) }
}
fun localProp(key: String, default: String = ""): String = localProps.getProperty(key, default).trim()

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.ksp)
    alias(libs.plugins.hilt)
}

android {
    namespace = "com.roadnovel"
    // AAOS CarPropertyManager (속도)
    useLibrary("android.car")
    compileSdk = 36

    defaultConfig {
        applicationId = "com.roadnovel"
        minSdk = 29
        targetSdk = 36
        versionCode = 1
        versionName = "0.1.0"

        // 음성 엔진: "android"(기본 TTS/STT) / "pleos"(Pleos SDK) / "mock"(자막만·버튼만)
        buildConfigField("String", "VOICE_ENGINE", "\"pleos\"")

        // Pleos 음성 모드: ON_DEVICE(앱 등록 불필요) / HYBRID(서버 우선, Client ID/Secret 필요)
        // local.properties 예:  pleos.mode=HYBRID   pleos.clientId=...   pleos.clientSecret=...
        // AI 추가 대사: "mock"(미리 쓴 대사, 기본) / "pleos"(Pleos LLM → 실패하면 Mock)
        // 에뮬레이터는 on-device LLM이 꺼져 있어 "pleos"로 해도 응답이 없다. SERVER는 local.properties의 Client ID 필요
        buildConfigField("String", "AI_ENGINE", "\"mock\"")

        buildConfigField("String", "PLEOS_MODE", "\"${localProp("pleos.mode", "ON_DEVICE")}\"")
        buildConfigField("String", "PLEOS_CLIENT_ID", "\"${localProp("pleos.clientId")}\"")
        buildConfigField("String", "PLEOS_CLIENT_SECRET", "\"${localProp("pleos.clientSecret")}\"")
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
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    buildFeatures {
        compose = true
        buildConfig = true
    }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.activity.compose)

    // Compose (BOM이 버전을 맞춰 줌)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.material3)
    debugImplementation(libs.androidx.compose.ui.tooling)

    // Hilt (의존성 주입)
    implementation(libs.hilt.android)
    ksp(libs.hilt.compiler)
    implementation(libs.androidx.hilt.lifecycle.viewmodel.compose)

    // 스토리 JSON 읽기, Flow
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.kotlinx.coroutines.core)

    // Room (진행 저장·배지)
    implementation(libs.androidx.room.runtime)
    implementation(libs.androidx.room.ktx)
    ksp(libs.androidx.room.compiler)

    // Pleos 음성 SDK (docs/Pleos_음성SDK_정리.md)
    implementation(libs.pleos.tts)
    implementation(libs.pleos.stt)
    // Pleos 차량 SDK (주행거리·기어)
    implementation(libs.pleos.vehicle)
    // Pleos LLM SDK (AI 추가 대사, 공식 문서 없음 — SDK jar로 확인)
    implementation(libs.pleos.llm)

    testImplementation(libs.junit)
}
