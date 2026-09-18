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
    implementation("org.springframework.boot:spring-boot-starter-data-jpa")
    implementation("com.fasterxml.jackson.module:jackson-module-kotlin")
    implementation("org.springframework.security:spring-security-core")
    // @SchedulerLock 어노테이션(스위퍼 메서드). 락 프로바이더 결선은 carry-app.
    implementation("net.javacrumbs.shedlock:shedlock-spring:7.10.1")

    // OpenAPI
    implementation("org.springdoc:springdoc-openapi-starter-webmvc-ui:2.7.0")

    testImplementation("io.mockk:mockk:1.13.13")
    testImplementation("org.assertj:assertj-core:3.27.0")
    testImplementation("com.tngtech.archunit:archunit-junit5:1.3.0")
}
