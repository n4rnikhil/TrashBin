package app.trashbin;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchRule;

/** Enforces the layering rules from docs/SYSTEM_ARCHITECTURE.md section 2.2. */
@AnalyzeClasses(packages = "app.trashbin", importOptions = ImportOption.DoNotIncludeTests.class)
class ArchitectureTest {

    @ArchTest
    static final ArchRule webLayerDoesNotUseRepositoriesOrDomainDirectly =
            noClasses()
                    .that()
                    .resideInAPackage("..web..")
                    .should()
                    .dependOnClassesThat()
                    .resideInAnyPackage("..repository..", "..domain..")
                    .allowEmptyShould(true);

    @ArchTest
    static final ArchRule servicesDoNotDependOnWeb =
            noClasses()
                    .that()
                    .resideInAPackage("..service..")
                    .should()
                    .dependOnClassesThat()
                    .resideInAPackage("..web..")
                    .allowEmptyShould(true);

    @ArchTest
    static final ArchRule domainDoesNotDependOnOuterLayers =
            noClasses()
                    .that()
                    .resideInAPackage("..domain..")
                    .should()
                    .dependOnClassesThat()
                    .resideInAnyPackage("..web..", "..service..", "..repository..")
                    .allowEmptyShould(true);
}
