plugins {
    kotlin("plugin.spring")
    kotlin("plugin.jpa")
    id("io.spring.dependency-management")
}

dependencyManagement {
    imports {
        mavenBom("org.springframework.boot:spring-boot-dependencies:4.1.1")
    }
}

dependencies {
    // Boot 4 는 Kafka 자동구성을 spring-boot-kafka 모듈로 분리했다 — spring-kafka 만으로는 KafkaTemplate·리스너 팩토리가 구성되지 않는다.
    api("org.springframework.boot:spring-boot-starter-kafka")
    implementation("org.springframework.boot:spring-boot-starter-data-jpa")
    implementation("tools.jackson.module:jackson-module-kotlin")
    implementation(project(":carry-common"))
    implementation(project(":carry-event"))
    implementation(project(":carry-infra-observability"))

    testImplementation("org.junit.jupiter:junit-jupiter")
    testImplementation("io.mockk:mockk:1.14.11")
    testImplementation("org.assertj:assertj-core:3.27.7")
    testImplementation("org.springframework.boot:spring-boot-starter-test")
    testImplementation("org.springframework.kafka:spring-kafka-test")
    testImplementation("org.awaitility:awaitility:4.3.0")
    // 멀티브로커 HA 무손실 IT — 3-node KRaft 클러스터 수동 와이어링(GenericContainer).
    testImplementation("org.testcontainers:testcontainers:2.0.5")
    testImplementation("org.testcontainers:testcontainers-junit-jupiter:2.0.5")
    testImplementation("org.apache.kafka:kafka-clients")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}
