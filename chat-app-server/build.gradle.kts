import com.google.protobuf.gradle.id

plugins {
	kotlin("jvm") version "2.2.21"
	kotlin("plugin.spring") version "2.2.21"
	id("org.springframework.boot") version "4.0.4"
	id("io.spring.dependency-management") version "1.1.7"
	id("com.google.protobuf") version "0.9.5"
	kotlin("plugin.jpa") version "2.2.21"
}

group = "com.example"
version = "0.0.1-SNAPSHOT"

java {
	toolchain {
		languageVersion = JavaLanguageVersion.of(17)
	}
}

repositories {
	mavenCentral()
}

extra["springGrpcVersion"] = "1.0.2"

dependencies {

	implementation("org.springframework.boot:spring-boot-starter")
	implementation("org.springframework.boot:spring-boot-starter-data-jdbc")

	implementation("net.devh:grpc-server-spring-boot-starter:2.15.0.RELEASE")

	implementation("io.grpc:grpc-kotlin-stub:1.4.1")
	implementation("io.grpc:grpc-stub:1.60.0")
	implementation("io.grpc:grpc-protobuf:1.60.0")

	implementation("org.jetbrains.kotlin:kotlin-reflect")
	implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core")
	implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.7.3")
	implementation("org.jetbrains.kotlinx:kotlinx-coroutines-reactive:1.7.3")
	implementation("org.springframework.boot:spring-boot-starter-data-jpa")
	implementation("net.devh:grpc-server-spring-boot-starter:2.15.0.RELEASE")

	implementation("org.postgresql:postgresql")
	runtimeOnly("org.postgresql:postgresql")

	testImplementation("org.jetbrains.kotlin:kotlin-test-junit5")
}

dependencyManagement {
	imports {
		mavenBom("org.springframework.grpc:spring-grpc-dependencies:${property("springGrpcVersion")}")
	}
}

kotlin {
	compilerOptions {
		freeCompilerArgs.addAll("-Xjsr305=strict", "-Xannotation-default-target=param-property")
	}
}

protobuf {
	protoc {
		artifact = "com.google.protobuf:protoc:3.25.0"
	}
	plugins {
		id("grpc") {
			artifact = "io.grpc:protoc-gen-grpc-java:1.60.0"
		}
		id("grpckt") {
			artifact = "io.grpc:protoc-gen-grpc-kotlin:1.4.1:jdk8@jar"
		}
	}
	generateProtoTasks {
		all().forEach { task ->
			task.plugins {
				id("grpc")
				id("grpckt")
			}
		}
	}
}

allOpen {
	annotation("jakarta.persistence.Entity")
	annotation("jakarta.persistence.MappedSuperclass")
	annotation("jakarta.persistence.Embeddable")
}

tasks.withType<Test> {
	useJUnitPlatform()
}
