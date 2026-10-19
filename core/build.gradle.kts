import org.jetbrains.kotlin.gradle.dsl.JvmTarget

// Android 非依存の純 Kotlin モジュール。RSS パーサーとクラスタリングをここに置き、JVM 上でテストする。
plugins {
    alias(libs.plugins.kotlin.jvm)
}

java {
    sourceCompatibility = JavaVersion.VERSION_17
    targetCompatibility = JavaVersion.VERSION_17
}

kotlin {
    compilerOptions {
        jvmTarget.set(JvmTarget.JVM_17)
    }
}

dependencies {
    // XmlPullParser API。Android ではフレームワークが実装を提供するので compileOnly。
    compileOnly(libs.xmlpull)
    testImplementation(libs.xmlpull)
    testImplementation(libs.kxml2)
    testImplementation(libs.junit)
}
