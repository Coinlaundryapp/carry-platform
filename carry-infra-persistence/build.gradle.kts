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
    api("org.springframework.boot:spring-boot-starter-data-jpa")
    // Boot 4 는 Flyway 자동구성을 spring-boot-flyway 모듈로 분리했다 — flyway-core 만 있으면 기동 시 마이그레이션이 돌지 않는다.
    api("org.springframework.boot:spring-boot-starter-flyway")
    api("org.flywaydb:flyway-database-postgresql")
    runtimeOnly("org.postgresql:postgresql")
}
