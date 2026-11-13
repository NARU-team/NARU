import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
}

android {
    namespace = "ai.naru.app"
    compileSdk = 35

    defaultConfig {
        applicationId = "ai.naru.app"
        // Pleos Connect 요구사항: 사이닝 키 로테이션 지원 때문에 API 28 이상
        minSdk = 28
        targetSdk = 35
        versionCode = 1
        versionName = "0.1.0"

        // 중계 서버 주소. local.properties의 naru.serverUrl (예: https://naru-server.vercel.app).
        // 비어 있으면 앱은 내장 샘플 데이터(DemoSiteRepository)를 쓴다.
        val localProperties = Properties().apply {
            rootProject.file("local.properties").takeIf { it.exists() }?.inputStream()?.use { load(it) }
        }
        buildConfigField("String", "SERVER_URL", "\"${localProperties.getProperty("naru.serverUrl", "")}\"")
    }

    // AAOS Car API(CarPropertyManager 등)를 쓰기 위한 선택 라이브러리.
    // 기어(GEAR_SELECTION) 조회에 필요하다.
    useLibrary("android.car")

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions {
        jvmTarget = "17"
    }
    buildFeatures {
        compose = true
        // BuildConfig.DEBUG로 개발용 차량 조작 패널을 디버그 빌드에서만 보여준다.
        buildConfig = true
    }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.activity.compose)

    // Compose BOM: Compose 라이브러리들의 버전을 한 묶음으로 맞춘다.
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.ui)
    implementation(libs.androidx.ui.tooling.preview)
    implementation(libs.androidx.material3)
    debugImplementation(libs.androidx.ui.tooling)

    // Pleos Connect SDK
    implementation(libs.pleos.vehicle)
    implementation(libs.pleos.fused.location)
    implementation(libs.pleos.text.to.speech)
    implementation(libs.pleos.speech.to.text)

    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)
    // 단위 테스트(JVM)에서는 Android의 org.json이 빈 껍데기라 실제 구현을 넣는다
    testImplementation(libs.json)
}
