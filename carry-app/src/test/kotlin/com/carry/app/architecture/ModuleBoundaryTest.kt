package com.carry.app.architecture

import com.tngtech.archunit.core.importer.ClassFileImporter
import com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses
import org.junit.jupiter.api.Test

class ModuleBoundaryTest {

    private val importedClasses = ClassFileImporter().importPackages("com.carry")

    @Test
    fun `event module should not depend on Spring`() {
        noClasses()
            .that().resideInAPackage("com.carry.event..")
            .should().dependOnClassesThat()
            .resideInAPackage("org.springframework..")
            .allowEmptyShould(true)
            .check(importedClasses)
    }

    @Test
    fun `domain layer should not depend on presentation layer`() {
        noClasses()
            .that().resideInAPackage("..domain..")
            .should().dependOnClassesThat()
            .resideInAPackage("..presentation..")
            .allowEmptyShould(true)
            .check(importedClasses)
    }
}
