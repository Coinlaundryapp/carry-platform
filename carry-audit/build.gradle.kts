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
    implementation("org.springframework.boot:spring-boot-starter-data-jpa")
    // RequestContextHolder(IP) + jakarta.servlet 타입
    implementation("org.springframework.boot:spring-boot-starter-web")
    // SecurityContextHolder(actor/role)
    implementation("org.springframework.security:spring-security-core")
    implementation("com.fasterxml.jackson.module:jackson-module-kotlin")

    testImplementation("io.mockk:mockk:1.14.11")
    testImplementation("org.assertj:assertj-core:3.27.7")
    testImplementation("com.tngtech.archunit:archunit-junit5:1.5.0")
    testImplementation("org.springframework.boot:spring-boot-starter-test")
}
