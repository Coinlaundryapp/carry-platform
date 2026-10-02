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
    // Foundation modules
    implementation(project(":carry-common"))
    implementation(project(":carry-infra-persistence"))

    // Spring
    implementation("org.springframework.boot:spring-boot-starter-web")
    implementation("org.springframework.boot:spring-boot-starter-data-jpa")

    // Kotlin
    implementation("tools.jackson.module:jackson-module-kotlin")

    // OpenAPI
    implementation("org.springdoc:springdoc-openapi-starter-webmvc-ui:3.1.1")

    // Test
    testImplementation("io.mockk:mockk:1.14.11")
    testImplementation("org.assertj:assertj-core:3.27.7")
    testImplementation("com.tngtech.archunit:archunit-junit5:1.5.0")
}
