package com.carry.audit.architecture

import com.tngtech.archunit.core.importer.ClassFileImporter
import com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses
import org.junit.jupiter.api.Test

class HexagonalArchitectureTest {

    private val classes = ClassFileImporter().importPackages("com.carry.audit")

    @Test
    fun `도메인 레이어는 어댑터 레이어에 의존하지 않는다`() {
        noClasses()
            .that().resideInAPackage("..domain..")
            .should().dependOnClassesThat()
            .resideInAPackage("..adapter..")
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
    fun `도메인 레이어는 JPA에 의존하지 않는다`() {
        noClasses()
            .that().resideInAPackage("..domain..")
            .should().dependOnClassesThat()
            .resideInAPackage("jakarta.persistence..")
            .check(classes)
    }

    @Test
    fun `포트 레이어는 어댑터 레이어에 의존하지 않는다`() {
        noClasses()
            .that().resideInAPackage("..port..")
            .should().dependOnClassesThat()
            .resideInAPackage("..adapter..")
            .check(classes)
    }
}
