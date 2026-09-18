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
    api("org.springframework:spring-tx")
    api("org.springframework.boot:spring-boot-starter-validation")
    api("org.springdoc:springdoc-openapi-starter-webmvc-ui:2.7.0")
    // @PreAuthorize 애너테이션과 AccessDeniedException(메서드 시큐리티 인가 실패 → GlobalExceptionHandler)
    // 은 컨트롤러 전반의 공통 관심사라 공통 모듈에서 전이 노출한다.
    api("org.springframework.security:spring-security-core")
    implementation("org.springframework.boot:spring-boot-starter-web")
    implementation("com.fasterxml.jackson.module:jackson-module-kotlin")

    testImplementation("org.assertj:assertj-core:3.27.7")
}
