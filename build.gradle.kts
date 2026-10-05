plugins {
    kotlin("jvm") version "2.4.20" apply false
    kotlin("plugin.spring") version "2.4.20" apply false
    kotlin("plugin.jpa") version "2.4.20" apply false
    id("org.springframework.boot") version "4.1.1" apply false
    id("io.spring.dependency-management") version "1.1.7" apply false
}

allprojects {
    // Spring Boot BOM(io.spring.dependency-management)이 kotlin.version 을 고정하므로,
    // Kotlin 플러그인만 올리면 kotlin-build-tools-api 등이 옛 버전으로 끌려와
    // "GRANULARITY is available only since 2.3.0" 으로 classpath 스냅샷 변환이 깨진다.
    extra["kotlin.version"] = "2.4.20"

    group = "com.carry"
    version = "0.0.1-SNAPSHOT"

    repositories {
        mavenCentral()
    }
}

subprojects {
    apply(plugin = "org.jetbrains.kotlin.jvm")

    configure<JavaPluginExtension> {
        toolchain {
            languageVersion.set(JavaLanguageVersion.of(21))
        }
    }

    tasks.withType<org.jetbrains.kotlin.gradle.tasks.KotlinCompile> {
        compilerOptions {
            freeCompilerArgs.addAll("-Xjsr305=strict")
            jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_21)
        }
    }

    tasks.withType<Test> {
        useJUnitPlatform()
    }

    dependencies {
        val implementation by configurations
        val testImplementation by configurations
        val testRuntimeOnly by configurations

        implementation(kotlin("stdlib"))
        // Spring Boot 4.1.1 BOM 의 junit-jupiter.version 과 맞춘다. BOM 을 가져오는 모듈에선 BOM 이 api·engine
        // 버전을 강제하므로, 여기만 따로 올리면 껍데기 집계 아티팩트만 바뀌고 실제 엔진은 그대로다(#203).
        testImplementation(platform("org.junit:junit-bom:6.1.3"))
        testImplementation("org.junit.jupiter:junit-jupiter")
        testRuntimeOnly("org.junit.platform:junit-platform-launcher")
    }
}
