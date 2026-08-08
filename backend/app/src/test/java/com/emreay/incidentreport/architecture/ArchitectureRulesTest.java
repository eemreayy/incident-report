package com.emreay.incidentreport.architecture;

import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchRule;

import static com.tngtech.archunit.library.dependencies.SlicesRuleDefinition.slices;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.fields;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

/**
 * Architecture rules the compiler cannot enforce.
 *
 * <p>The module boundary itself is already a build guarantee: {@code ingestion} and {@code analysis}
 * declare no dependency on each other, so reaching across is a compile error (ADR-001). What is left
 * for this test is everything the dependency graph cannot express — which library a module is
 * allowed to touch, whether a controller leaks a persistence type, how beans are injected.
 *
 * <p>This test lives in {@code app} because that is the only module with every other module on its
 * classpath. See ADR-017 for why ArchUnit rather than Spring Modulith.
 */
@AnalyzeClasses(
        packages = ArchitectureRulesTest.ROOT_PACKAGE,
        // Rules describe production code. Tests legitimately do things production code must not —
        // reaching across modules to assemble a scenario, for one — and holding them to the same
        // rules would only teach people to weaken the rules.
        importOptions = ImportOption.DoNotIncludeTests.class)
class ArchitectureRulesTest {

    static final String ROOT_PACKAGE = "com.emreay.incidentreport";

    private static final String INGESTION = ROOT_PACKAGE + ".ingestion..";
    private static final String ANALYSIS = ROOT_PACKAGE + ".analysis..";
    private static final String REALTIME = ROOT_PACKAGE + ".realtime..";
    private static final String SHARED = ROOT_PACKAGE + ".shared..";

    // ---------------------------------------------------------------------
    // Module boundaries
    //
    // Redundant with the Maven dependency graph today, and deliberately so: the
    // rule states the intent in one readable place, and it still fires if someone
    // "fixes" a compile error by adding the dependency to a pom.
    // ---------------------------------------------------------------------

    @ArchTest
    static final ArchRule ingestionMustNotDependOnAnalysis =
            noClasses().that().resideInAPackage(INGESTION)
                    .should().dependOnClassesThat().resideInAPackage(ANALYSIS)
                    .because("the two modules communicate through domain events in shared, never directly (ADR-003)");

    @ArchTest
    static final ArchRule analysisMustNotDependOnIngestion =
            noClasses().that().resideInAPackage(ANALYSIS)
                    .should().dependOnClassesThat().resideInAPackage(INGESTION)
                    .because("the two modules communicate through domain events in shared, never directly (ADR-003)");

    @ArchTest
    static final ArchRule sharedMustNotDependOnAnyModule =
            noClasses().that().resideInAPackage(SHARED)
                    .should().dependOnClassesThat().resideInAnyPackage(INGESTION, ANALYSIS, REALTIME)
                    .because("the shared kernel is depended upon, it does not depend back");

    @ArchTest
    static final ArchRule modulesMustNotFormCycles =
            slices().matching(ROOT_PACKAGE + ".(*)..").should().beFreeOfCycles();

    // ---------------------------------------------------------------------
    // Database ownership (ADR-002)
    //
    // Each module's pom already withholds the other database's driver, so this is
    // a second line of defence: it fires the moment someone adds the dependency.
    // ---------------------------------------------------------------------

    @ArchTest
    static final ArchRule analysisMustNotTouchMongo =
            noClasses().that().resideInAPackage(ANALYSIS)
                    .should().dependOnClassesThat()
                    .resideInAnyPackage("com.mongodb..", "org.bson..", "org.springframework.data.mongodb..")
                    .because("MongoDB belongs to the ingestion module (ADR-002)");

    @ArchTest
    static final ArchRule ingestionMustNotTouchRelationalPersistence =
            noClasses().that().resideInAPackage(INGESTION)
                    .should().dependOnClassesThat()
                    .resideInAnyPackage("jakarta.persistence..", "org.springframework.data.jpa..",
                            "org.hibernate..", "javax.sql..", "org.springframework.jdbc..")
                    .because("PostgreSQL belongs to the analysis module (ADR-002)");

    @ArchTest
    static final ArchRule realtimeMustNotTouchAnyDatabase =
            noClasses().that().resideInAPackage(REALTIME)
                    .should().dependOnClassesThat()
                    .resideInAnyPackage("com.mongodb..", "org.bson..", "jakarta.persistence..",
                            "org.springframework.data..", "org.hibernate..")
                    .because("realtime is a transport layer, not a source of truth");

    // ---------------------------------------------------------------------
    // Layering and API surface
    // ---------------------------------------------------------------------

    @ArchTest
    static final ArchRule controllersMustNotExposePersistenceTypes =
            noClasses().that().areAnnotatedWith("org.springframework.web.bind.annotation.RestController")
                    .or().areAnnotatedWith("org.springframework.stereotype.Controller")
                    .should().dependOnClassesThat()
                    .areAnnotatedWith("jakarta.persistence.Entity")
                    .orShould().dependOnClassesThat()
                    .areAnnotatedWith("org.springframework.data.mongodb.core.mapping.Document")
                    .because("controllers speak DTOs; entities and documents never cross the HTTP boundary");

    @ArchTest
    static final ArchRule repositoriesMustNotDependOnControllers =
            noClasses().that().haveSimpleNameEndingWith("Repository")
                    .should().dependOnClassesThat().haveSimpleNameEndingWith("Controller")
                    .because("dependencies point inwards: controller -> service -> repository");

    @ArchTest
    static final ArchRule repositoriesMustNotDependOnServices =
            noClasses().that().haveSimpleNameEndingWith("Repository")
                    .should().dependOnClassesThat().haveSimpleNameEndingWith("Service")
                    .because("dependencies point inwards: controller -> service -> repository");

    // ---------------------------------------------------------------------
    // Conventions
    // ---------------------------------------------------------------------

    @ArchTest
    static final ArchRule noFieldInjection =
            fields().should().notBeAnnotatedWith("org.springframework.beans.factory.annotation.Autowired")
                    .because("constructor injection only: it makes dependencies explicit and the class testable "
                            + "without a Spring context");

    /**
     * Guards the rules above against silently passing on a typo.
     *
     * <p>{@code archRule.failOnEmptyShould} is switched off in {@code archunit.properties} because
     * most modules are still empty, so a mistyped package name would make a rule match nothing and
     * report success. This check keeps that from going unnoticed: as soon as the project has
     * classes at all, it fails if none of them are under the root package.
     */
    @ArchTest
    static void projectClassesAreActuallyBeingAnalysed(JavaClasses classes) {
        if (classes.isEmpty()) {
            throw new AssertionError("ArchUnit imported no classes at all from " + ROOT_PACKAGE
                    + " - the rules above would all pass vacuously. Check the package name.");
        }
    }
}
