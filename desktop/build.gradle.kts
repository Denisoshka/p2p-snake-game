import org.jetbrains.compose.desktop.application.dsl.TargetFormat

plugins {
  alias(libs.plugins.kotlin.multiplatform)
  alias(libs.plugins.kotlin.plugin)
  alias(libs.plugins.compose.compiler)
  alias(libs.plugins.kotlin.kapt)
}

group = "d.zhdanov.ccfit.nsu"
version = "1.0-SNAPSHOT"

val protobufVersion = "4.28.2"

repositories {
  gradlePluginPortal()
  mavenCentral()
  google()
}

kotlin {
  jvm("desktop") {
    withJava()
  }

  sourceSets {
    val desktopMain by getting
    desktopMain.dependencies {

      implementation(compose.desktop.currentOs)
      implementation(libs.netty)
      implementation(libs.mapstruct)
      implementation(libs.mapstruct.processor)
      implementation(libs.kotlin.logging)
      implementation(libs.compose.runtime)
      implementation(libs.compose.foundation)
      implementation(libs.compose.material)
      implementation(libs.protobuf.util)
      implementation(libs.protobuf.core)
      implementation(libs.kotlinx.coroutines.core)
      implementation(libs.kotlinx.coroutines.jvm)
      implementation(libs.androin.lifecycle.viewmodel)
      implementation(project(":proto"))
    }
  }
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