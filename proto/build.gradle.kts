plugins {
  java
  alias(libs.plugins.protobuf)
}

repositories {
  gradlePluginPortal()
  mavenCentral()
}

dependencies {
  implementation(libs.protobuf.util)
  implementation(libs.protobuf.core)
}

protobuf {
  protoc {
    artifact = libs.protobuf.protoc.get().toString()
  }
}
