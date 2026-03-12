plugins {
    kotlin("plugin.spring")
    kotlin("plugin.jpa")
    id("org.springframework.boot")
    id("io.spring.dependency-management")
}

dependencies {
    implementation(project(":carry-common"))
    implementation(project(":carry-event"))
    implementation(project(":carry-infra-persistence"))
    implementation(project(":carry-infra-kafka"))
    implementation(project(":carry-infra-redis"))
    implementation(project(":carry-infra-s3"))
    implementation(project(":carry-infra-observability"))
    implementation(project(":carry-security"))

    // Domain modules
    implementation(project(":carry-user"))
    implementation(project(":carry-laundromat"))
    implementation(project(":carry-price"))
    implementation(project(":carry-geo"))
    implementation(project(":carry-order"))
    implementation(project(":carry-dispatch"))
    implementation(project(":carry-payment"))
    implementation(project(":carry-delivery"))
    // implementation(project(":carry-operation"))
    // implementation(project(":carry-review"))
    // implementation(project(":carry-notification"))
    // implementation(project(":carry-media"))
    // implementation(project(":carry-service-availability"))

    implementation("org.springframework.boot:spring-boot-starter-web")
    implementation("com.fasterxml.jackson.module:jackson-module-kotlin")
    implementation("org.springdoc:springdoc-openapi-starter-webmvc-ui:2.7.0")

    testImplementation("org.springframework.boot:spring-boot-starter-test")
    testImplementation("com.tngtech.archunit:archunit-junit5:1.3.0")
}
