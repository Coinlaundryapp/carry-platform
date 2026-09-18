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
    implementation(project(":carry-common"))
    implementation(project(":carry-infra-redis"))
    implementation("org.springframework.boot:spring-boot-starter-web")
    implementation("com.fasterxml.jackson.module:jackson-module-kotlin")

    // OpenAPI
    implementation("org.springdoc:springdoc-openapi-starter-webmvc-ui:2.7.0")

    // Resilience4j Circuit Breaker — Naver 지오코딩 API 장애 시 fast-fail로 스레드 블로킹 방지
    implementation("io.github.resilience4j:resilience4j-spring-boot3:2.2.0")

    testImplementation("io.mockk:mockk:1.13.13")
    testImplementation("org.assertj:assertj-core:3.27.0")
    testImplementation("com.tngtech.archunit:archunit-junit5:1.3.0")
    // ApplicationContextRunner — 데코레이터 체인 와이어링 회귀 가드(GeocodingResilienceConfigTest)
    testImplementation("org.springframework.boot:spring-boot-starter-test")
}
