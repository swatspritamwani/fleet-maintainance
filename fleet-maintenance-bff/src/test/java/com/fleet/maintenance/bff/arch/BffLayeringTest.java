package com.fleet.maintenance.bff.arch;

import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import org.junit.jupiter.api.Test;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

class BffLayeringTest {

    private static final JavaClasses ALL_CLASSES = new ClassFileImporter()
        .withImportOption(ImportOption.Predefined.DO_NOT_INCLUDE_TESTS)
        .importPackages("com.fleet.maintenance");

    @Test
    void controllers_must_not_access_domain_ports_directly() {
        noClasses()
            .that().resideInAPackage("..bff.controller..")
            .should().dependOnClassesThat().resideInAPackage("..domain.port..")
            .check(ALL_CLASSES);
    }

    @Test
    void controllers_must_not_access_infrastructure_directly() {
        noClasses()
            .that().resideInAPackage("..bff.controller..")
            .should().dependOnClassesThat().resideInAPackage("..infrastructure..")
            .check(ALL_CLASSES);
    }

    @Test
    void application_services_must_not_depend_on_bff() {
        noClasses()
            .that().resideInAPackage("..application..")
            .should().dependOnClassesThat().resideInAPackage("..bff..")
            .check(ALL_CLASSES);
    }

    @Test
    void application_services_must_not_depend_on_infrastructure() {
        noClasses()
            .that().resideInAPackage("..application..")
            .should().dependOnClassesThat().resideInAPackage("..infrastructure..")
            .check(ALL_CLASSES);
    }

    @Test
    void domain_must_not_import_spring() {
        noClasses()
            .that().resideInAPackage("..domain..")
            .should().dependOnClassesThat().resideInAPackage("org.springframework..")
            .check(ALL_CLASSES);
    }

    @Test
    void domain_must_not_import_aws_sdk() {
        noClasses()
            .that().resideInAPackage("..domain..")
            .should().dependOnClassesThat().resideInAPackage("software.amazon.awssdk..")
            .check(ALL_CLASSES);
    }

    @Test
    void domain_must_not_import_kafka() {
        noClasses()
            .that().resideInAPackage("..domain..")
            .should().dependOnClassesThat().resideInAPackage("org.apache.kafka..")
            .check(ALL_CLASSES);
    }
}
