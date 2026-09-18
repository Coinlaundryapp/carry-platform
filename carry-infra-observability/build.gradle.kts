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
    api("org.springframework.boot:spring-boot-starter-actuator")
    api("io.micrometer:micrometer-tracing-bridge-otel")
    api("io.opentelemetry:opentelemetry-api")
    api("io.opentelemetry:opentelemetry-exporter-otlp")
    api("io.micrometer:micrometer-registry-prometheus")

    // Kafka tracing propagation
    api("io.micrometer:micrometer-tracing")

    // Structured JSON logging
    api("net.logstash.logback:logstash-logback-encoder:9.0")

    testImplementation("org.junit.jupiter:junit-jupiter:5.11.3")
    testImplementation("org.assertj:assertj-core:3.27.7")
}
