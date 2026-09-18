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
    // Foundation modules
    implementation(project(":carry-common"))
    implementation(project(":carry-infra-persistence"))
    // 토큰 발급/검증(JwtProvider). AuthTokenPort 어댑터에서만 사용 — 사이클 없음.
    implementation(project(":carry-security"))

    // Spring
    implementation("org.springframework.boot:spring-boot-starter-web")
    implementation("org.springframework.boot:spring-boot-starter-data-jpa")
    implementation("org.springframework.boot:spring-boot-starter-security")
    // refresh 토큰 회전/폐기 allowlist(StringRedisTemplate). geo 모듈과 동일하게 어댑터에서만 사용.
    implementation("org.springframework.boot:spring-boot-starter-data-redis")

    // Kotlin
    implementation("com.fasterxml.jackson.module:jackson-module-kotlin")

    // OpenAPI
    implementation("org.springdoc:springdoc-openapi-starter-webmvc-ui:2.7.0")

    // Test
    testImplementation("io.mockk:mockk:1.14.11")
    testImplementation("org.assertj:assertj-core:3.27.7")
    testImplementation("com.tngtech.archunit:archunit-junit5:1.5.0")
    testImplementation("org.springframework:spring-test") // MockRestServiceServer (KakaoOAuthClient)
}
