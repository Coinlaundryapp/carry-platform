plugins {
    kotlin("plugin.spring")
    kotlin("plugin.jpa")
    id("io.spring.dependency-management")
}

dependencyManagement {
    imports {
        mavenBom("org.springframework.boot:spring-boot-dependencies:3.4.1")
    }
}

dependencies {
    implementation(project(":carry-common"))
    implementation(project(":carry-event"))
    implementation(project(":carry-audit"))
    implementation(project(":carry-infra-persistence"))
    implementation(project(":carry-infra-kafka"))
    implementation(project(":carry-infra-observability"))
    implementation("org.springframework.boot:spring-boot-starter-web")
    implementation("org.springframework.security:spring-security-core")
    implementation("org.springframework.boot:spring-boot-starter-data-jpa")
    implementation("com.fasterxml.jackson.module:jackson-module-kotlin")

    // OpenAPI
    implementation("org.springdoc:springdoc-openapi-starter-webmvc-ui:2.7.0")

    // Resilience4j Circuit Breaker — PG 장애 시 fast-fail로 스레드 블로킹 방지
    implementation("io.github.resilience4j:resilience4j-spring-boot3:2.2.0")

    testImplementation("io.mockk:mockk:1.13.13")
    testImplementation("org.assertj:assertj-core:3.27.0")
    testImplementation("com.tngtech.archunit:archunit-junit5:1.3.0")
}
