package app.trashbin;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

import app.trashbin.common.persistence.OwnedEntityRepository;
import com.tngtech.archunit.base.DescribedPredicate;
import com.tngtech.archunit.core.domain.JavaCall;
import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchRule;
import java.util.Set;

/** Enforces the layering rules from docs/SYSTEM_ARCHITECTURE.md section 2.2. */
@AnalyzeClasses(packages = "app.trashbin", importOptions = ImportOption.DoNotIncludeTests.class)
class ArchitectureTest {

    private static final Set<String> ID_ONLY_METHODS =
            Set.of("findById", "getReferenceById", "getById", "existsById", "deleteById");

    private static final DescribedPredicate<JavaCall<?>> ID_ONLY_LOOKUP_ON_OWNED_REPOSITORY =
            new DescribedPredicate<>("an id-only lookup on a repository of owned entities") {
                @Override
                public boolean test(JavaCall<?> call) {
                    return ID_ONLY_METHODS.contains(call.getName())
                            && call.getTargetOwner().isAssignableTo(OwnedEntityRepository.class);
                }
            };

    @ArchTest
    static final ArchRule webLayerDoesNotUseRepositoriesOrDomainDirectly =
            noClasses()
                    .that()
                    .resideInAPackage("..web..")
                    .should()
                    .dependOnClassesThat()
                    .resideInAnyPackage("app.trashbin..repository..", "app.trashbin..domain..")
                    .allowEmptyShould(true);

    @ArchTest
    static final ArchRule servicesDoNotDependOnWeb =
            noClasses()
                    .that()
                    .resideInAPackage("..service..")
                    .should()
                    .dependOnClassesThat()
                    // Services are framework-neutral: no controllers, no Spring MVC types.
                    .resideInAnyPackage("app.trashbin..web..", "org.springframework.web..")
                    .allowEmptyShould(true);

    @ArchTest
    static final ArchRule domainDoesNotDependOnOuterLayers =
            noClasses()
                    .that()
                    .resideInAPackage("..domain..")
                    .should()
                    .dependOnClassesThat()
                    .resideInAnyPackage(
                            "app.trashbin..web..",
                            "app.trashbin..service..",
                            "app.trashbin..repository..")
                    .allowEmptyShould(true);

    /** Owned data is always looked up together with its owner (CLAUDE.md invariant 1). */
    @ArchTest
    static final ArchRule ownedEntitiesAreNeverLoadedByIdAlone =
            noClasses().should().callMethodWhere(ID_ONLY_LOOKUP_ON_OWNED_REPOSITORY);

    /** Tags are a building block of the archive, never the other way round (no cycles). */
    @ArchTest
    static final ArchRule tagsDoNotDependOnTheArchive =
            noClasses()
                    .that()
                    .resideInAPackage("app.trashbin.tag..")
                    .should()
                    .dependOnClassesThat()
                    .resideInAPackage("app.trashbin.archive..");

    /** Collections, like tags, are a building block of the archive (no cycles). */
    @ArchTest
    static final ArchRule collectionsDoNotDependOnTheArchive =
            noClasses()
                    .that()
                    .resideInAPackage("app.trashbin.collection..")
                    .should()
                    .dependOnClassesThat()
                    .resideInAPackage("app.trashbin.archive..");

    /** Storage must not know about the modules that use it (they implement StorageCatalog). */
    @ArchTest
    static final ArchRule storageDoesNotDependOnFeatureModules =
            noClasses()
                    .that()
                    .resideInAPackage("app.trashbin.storage..")
                    .should()
                    .dependOnClassesThat()
                    .resideInAnyPackage("app.trashbin.archive..", "app.trashbin.user..");
}
