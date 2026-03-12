package com.carry.user.architecture

import com.tngtech.archunit.core.importer.ClassFileImporter
import com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses
import org.junit.jupiter.api.Test

class HexagonalArchitectureTest {

    private val classes = ClassFileImporter().importPackages("com.carry.user")

    @Test
    fun `도메인 레이어는 애플리케이션 레이어에 의존하지 않는다`() {
        noClasses()
            .that().resideInAPackage("..domain..")
            .should().dependOnClassesThat()
            .resideInAPackage("..application..")
            .check(classes)
    }

    @Test
    fun `도메인 레이어는 어댑터 레이어에 의존하지 않는다`() {
        noClasses()
            .that().resideInAPackage("..domain..")
            .should().dependOnClassesThat()
            .resideInAPackage("..adapter..")
            .check(classes)
    }

    @Test
    fun `도메인 레이어는 JPA에 의존하지 않는다`() {
        noClasses()
            .that().resideInAPackage("..domain..")
            .should().dependOnClassesThat()
            .resideInAPackage("jakarta.persistence..")
            .check(classes)
    }

    @Test
    fun `도메인 레이어는 Spring에 의존하지 않는다`() {
        noClasses()
            .that().resideInAPackage("..domain..")
            .should().dependOnClassesThat()
            .resideInAPackage("org.springframework..")
            .check(classes)
    }

    @Test
    fun `애플리케이션 레이어는 어댑터 레이어에 의존하지 않는다`() {
        noClasses()
            .that().resideInAPackage("..application..")
            .should().dependOnClassesThat()
            .resideInAPackage("..adapter..")
            .check(classes)
    }

    @Test
    fun `애플리케이션 레이어는 JPA에 의존하지 않는다`() {
        noClasses()
            .that().resideInAPackage("..application..")
            .should().dependOnClassesThat()
            .resideInAPackage("jakarta.persistence..")
            .check(classes)
    }

    @Test
    fun `인바운드 어댑터는 아웃바운드 어댑터에 의존하지 않는다`() {
        noClasses()
            .that().resideInAPackage("..adapter.inbound..")
            .should().dependOnClassesThat()
            .resideInAPackage("..adapter.outbound..")
            .check(classes)
    }
}
