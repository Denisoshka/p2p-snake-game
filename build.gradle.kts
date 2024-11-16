import org.jetbrains.compose.desktop.application.dsl.TargetFormat

plugins {
  alias(libs.plugins.kotlin.jvm)
  alias(libs.plugins.kotlin.kapt)
  alias(libs.plugins.multiplatform)
  alias(libs.plugins.compose.plugin)
  alias(libs.plugins.compose.compiler)
  alias(libs.plugins.protobuf)
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

dependencies {
  testImplementation(kotlin("test"))

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

  kapt(libs.mapstruct.processor)
}

kapt {
  arguments {
  }
}

protobuf {
  protoc {
    artifact = "com.google.protobuf:protoc:${protobufVersion}"
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