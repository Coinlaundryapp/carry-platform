plugins {
    kotlin("plugin.spring")
    id("io.spring.dependency-management")
}

dependencyManagement {
    imports {
        mavenBom("org.springframework.boot:spring-boot-dependencies:4.1.1")
    }
}

dependencies {
    implementation(project(":carry-common"))
    api("org.springframework.boot:spring-boot-starter-actuator")
    // Boot 4 는 트레이싱·OTLP 내보내기 자동구성을 별도 모듈로 분리했다. starter-opentelemetry 는 OTLP 메트릭
    // 레지스트리까지 끌어와 기존에 없던 메트릭 내보내기를 켜므로, 트레이싱 모듈만 명시한다.
    api("org.springframework.boot:spring-boot-micrometer-tracing-opentelemetry")
    api("org.springframework.boot:spring-boot-opentelemetry")
    api("io.micrometer:micrometer-tracing-bridge-otel")
    api("io.opentelemetry:opentelemetry-api")
    api("io.opentelemetry:opentelemetry-exporter-otlp")
    api("io.micrometer:micrometer-registry-prometheus")

    // Kafka tracing propagation
    api("io.micrometer:micrometer-tracing")

    // Structured JSON logging
    api("net.logstash.logback:logstash-logback-encoder:9.0")

    testImplementation("org.junit.jupiter:junit-jupiter")
    testImplementation("org.assertj:assertj-core:3.27.7")
}
