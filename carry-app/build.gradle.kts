plugins {
    kotlin("plugin.spring")
    kotlin("plugin.jpa")
    id("org.springframework.boot")
    id("io.spring.dependency-management")
}

dependencies {
    implementation(project(":carry-common"))
    implementation(project(":carry-event"))
    implementation(project(":carry-audit"))
    implementation(project(":carry-infra-persistence"))
    implementation(project(":carry-infra-kafka"))
    implementation(project(":carry-infra-redis"))
    implementation(project(":carry-infra-s3"))
    implementation(project(":carry-infra-observability"))
    implementation(project(":carry-security"))

    // Domain modules
    implementation(project(":carry-user"))
    implementation(project(":carry-laundromat"))
    implementation(project(":carry-price"))
    implementation(project(":carry-geo"))
    implementation(project(":carry-order"))
    implementation(project(":carry-dispatch"))
    implementation(project(":carry-payment"))
    implementation(project(":carry-delivery"))
    implementation(project(":carry-operation"))
    implementation(project(":carry-review"))
    implementation(project(":carry-notification"))
    implementation(project(":carry-media"))
    implementation(project(":carry-service-availability"))

    implementation("org.springframework.boot:spring-boot-starter-web")
    implementation("com.fasterxml.jackson.module:jackson-module-kotlin")
    implementation("org.springdoc:springdoc-openapi-starter-webmvc-ui:2.7.0")

    // 분산 스케줄러 락(멀티 인스턴스에서 @Scheduled 1회 실행). 락 프로바이더는 Redis(prod) / NoOp(test·단일).
    implementation("net.javacrumbs.shedlock:shedlock-spring:7.10.1")
    implementation("net.javacrumbs.shedlock:shedlock-provider-redis-spring:7.10.1")

    testImplementation("org.springframework.boot:spring-boot-starter-test")
    testImplementation("org.springframework.security:spring-security-test")
    testImplementation("com.tngtech.archunit:archunit-junit5:1.3.0")
    testImplementation("org.testcontainers:testcontainers:1.20.4")
    testImplementation("org.testcontainers:postgresql:1.20.4")
    testImplementation("org.testcontainers:junit-jupiter:1.20.4")
    testImplementation("org.testcontainers:toxiproxy:1.20.4")
    testImplementation("com.ninja-squad:springmockk:4.0.2")
    testImplementation("io.mockk:mockk:1.13.13")
    testImplementation("org.assertj:assertj-core:3.27.0")
    testImplementation("net.ttddyy:datasource-proxy:1.10.1")
    testImplementation(testFixtures(project(":carry-order")))
    testImplementation(testFixtures(project(":carry-delivery")))
}
