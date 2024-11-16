plugins {
  id("java")
  id("com.google.protobuf") version "0.9.4"
}

repositories {
  gradlePluginPortal()
  mavenCentral()
}


val protobufVersion = "4.28.2"

dependencies {
  implementation("com.google.protobuf:protobuf-java:${protobufVersion}")
  implementation("com.google.protobuf:protobuf-java-util:${protobufVersion}")
}

protobuf {
  protoc {
    artifact = "com.google.protobuf:protoc:${protobufVersion}"
  }
}
