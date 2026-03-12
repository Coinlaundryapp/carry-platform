plugins {
    kotlin("plugin.spring")
    id("io.spring.dependency-management")
    `java-library`
}

dependencyManagement {
    imports {
        mavenBom("org.springframework.boot:spring-boot-dependencies:3.4.1")
    }
}

dependencies {
    api("org.springframework:spring-web")
    api("org.springframework.boot:spring-boot-starter-validation")
    api("org.springdoc:springdoc-openapi-starter-webmvc-ui:2.7.0")
    implementation("org.springframework.boot:spring-boot-starter-web")
    implementation("com.fasterxml.jackson.module:jackson-module-kotlin")
}
