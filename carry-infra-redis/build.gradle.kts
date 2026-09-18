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
    api("org.springframework.boot:spring-boot-starter-data-redis")

    testImplementation("io.mockk:mockk:1.14.11")
    testImplementation("org.assertj:assertj-core:3.27.7")
}
