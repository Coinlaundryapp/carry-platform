plugins {
    kotlin("plugin.spring")
    id("io.spring.dependency-management")
}

dependencyManagement {
    imports {
        mavenBom("org.springframework.boot:spring-boot-dependencies:4.1.2")
    }
}

dependencies {
    api("software.amazon.awssdk:s3:2.57.3")
    implementation("org.springframework.boot:spring-boot-starter")
}
