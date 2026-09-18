plugins {
    kotlin("plugin.spring")
    id("io.spring.dependency-management")
}

dependencyManagement {
    imports {
        mavenBom("org.springframework.boot:spring-boot-dependencies:3.4.1")
    }
}

dependencies {
    api("software.amazon.awssdk:s3:2.54.18")
    implementation("org.springframework.boot:spring-boot-starter")
}
