import org.jetbrains.compose.desktop.application.dsl.TargetFormat

plugins {
  alias(libs.plugins.kotlin.multiplatform)
  alias(libs.plugins.kotlin.plugin)
  alias(libs.plugins.compose.compiler)
//  alias(libs.plugins.kotlin.jvm)
  alias(libs.plugins.protobuf)
  alias(libs.plugins.kotlin.kapt)
  id("java")
}

java {
  toolchain {
    languageVersion.set(JavaLanguageVersion.of(22))
  }
}

group = "d.zhdanov.ccfit.nsu"
version = "1.0-SNAPSHOT"

val protobufVersion = "4.28.2"

repositories {
  gradlePluginPortal()
  mavenCentral()
}

kotlin {
  jvm("desktop") {
    withJava() // Включение совместимости с Java
  }
  sourceSets {
    val desktopMain by getting {
      dependencies {
//        testImplementation(kotlin("test"))

        runtimeOnly(libs.coroutines.jvm)

        implementation(compose.desktop.currentOs)
        implementation(libs.protobuf.util)
        implementation(libs.protobuf.core)
        implementation(libs.netty)
        implementation(libs.coroutines.core)
        implementation(libs.mapstruct)
        implementation(libs.kotlin.logging)
        implementation(libs.compose.runtime)
        implementation(libs.compose.foundation)
        implementation(libs.compose.material)

//      kapt(libs.mapstruct.processor)
      }
    }
  }
}


protobuf {
  protoc {
    artifact = "com.google.protobuf:protoc:${protobufVersion}"
  }
}

kapt {
  arguments {
  }
}



tasks.test {
  useJUnitPlatform()
}

compose.desktop {
  application {
    mainClass = "MainKt"
    nativeDistributions {
      targetFormats(TargetFormat.Dmg, TargetFormat.Msi, TargetFormat.Deb)
      packageVersion = "1.0.0"
    }
  }
}