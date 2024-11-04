plugins {
  kotlin("jvm") version "2.0.21"
  id("com.google.protobuf") version "0.9.4"
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
  runtimeOnly("org.jetbrains.kotlinx:kotlinx-coroutines-core-jvm:1.9.0")
  implementation("com.google.protobuf:protobuf-java-util:${protobufVersion}")
  implementation("com.google.protobuf:protobuf-java:${protobufVersion}")
  implementation("io.netty:netty-all:4.1.114.Final")
  implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.8.1")
//  runtimeOnly("io.github.oshai:kotlin-logging-jvm:7.0.0")
  implementation("io.github.oshai:kotlin-logging-jvm:7.0.0")

}

protobuf {
  protoc {
    artifact = "com.google.protobuf:protoc:${protobufVersion}"
  }
}

tasks.test {
  useJUnitPlatform()
}

protobuf {
  protoc {
    artifact = "com.google.protobuf:protoc:${protobufVersion}"
  }
}