package com.africe.backend;

import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

class ArchitectureTest {

    private static JavaClasses classes;

    @BeforeAll
    static void setup() {
        classes = new ClassFileImporter()
                .withImportOption(ImportOption.Predefined.DO_NOT_INCLUDE_TESTS)
                .importPackages("com.africe.backend");
    }

    @Test
    void models_should_not_depend_on_services() {
        noClasses()
                .that().resideInAnyPackage("..model..")
                .should().dependOnClassesThat().resideInAnyPackage("..service..")
                .check(classes);
    }

    @Test
    void services_should_not_depend_on_controllers() {
        noClasses()
                .that().resideInAnyPackage("..service..")
                .should().dependOnClassesThat().resideInAnyPackage("..controller..")
                .check(classes);
    }

    @Test
    void models_should_not_depend_on_controllers() {
        noClasses()
                .that().resideInAnyPackage("..model..")
                .should().dependOnClassesThat().resideInAnyPackage("..controller..")
                .check(classes);
    }

    @Test
    void models_should_not_depend_on_repositories() {
        noClasses()
                .that().resideInAnyPackage("..model..")
                .should().dependOnClassesThat().resideInAnyPackage("..repository..")
                .check(classes);
    }

    @Test
    void dto_should_not_depend_on_services() {
        noClasses()
                .that().resideInAnyPackage("..dto..")
                .should().dependOnClassesThat().resideInAnyPackage("..service..")
                .check(classes);
    }
}
