package com.pedeai;

import com.tngtech.archunit.core.domain.Dependency;
import com.tngtech.archunit.core.domain.JavaClass;
import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchCondition;
import com.tngtech.archunit.lang.ArchRule;
import com.tngtech.archunit.lang.ConditionEvents;
import com.tngtech.archunit.lang.SimpleConditionEvent;
import jakarta.persistence.Entity;

import static com.tngtech.archunit.base.DescribedPredicate.not;
import static com.tngtech.archunit.core.domain.JavaClass.Predicates.resideInAPackage;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;
import static com.tngtech.archunit.library.dependencies.SlicesRuleDefinition.slices;

/** Regras de arquitetura de docs/02-arquitetura.md, verificadas a cada build. */
@AnalyzeClasses(packages = "com.pedeai", importOptions = ImportOption.DoNotIncludeTests.class)
class ArchitectureTest {
    private static final String ROOT = "com.pedeai.";

    @ArchTest
    static final ArchRule controllersDoNotUseRepositories = noClasses()
            .that().resideInAPackage("..controller..")
            .should().dependOnClassesThat().resideInAPackage("..repository..")
            .because("controller só conversa com service");

    @ArchTest
    static final ArchRule controllersDoNotExposeEntities = noClasses()
            .that().resideInAPackage("..controller..")
            .should().dependOnClassesThat().areAnnotatedWith(Entity.class)
            .because("a API devolve DTOs, nunca entidades JPA");

    @ArchTest
    static final ArchRule repositoriesAreUsedOnlyByServices = classes()
            .that().resideInAPackage("..repository..")
            .should().onlyBeAccessed().byAnyPackage("..service..", "..repository..");

    @ArchTest
    static final ArchRule servicesDoNotDependOnControllers = noClasses()
            .that().resideInAPackage("..service..")
            .should().dependOnClassesThat().resideInAPackage("..controller..");

    @ArchTest
    static final ArchRule sharedDoesNotDependOnModules = noClasses()
            .that().resideInAPackage("com.pedeai.shared..")
            .should().dependOnClassesThat(resideInAPackage("com.pedeai..")
                    .and(not(resideInAPackage("com.pedeai.shared..")))
                    .and(not(resideInAPackage("com.pedeai"))))
            .because("shared é a base de todos os módulos e não pode conhecer nenhum deles");

    @ArchTest
    static final ArchRule modulesAreFreeOfCycles = slices().matching("com.pedeai.(*)..").should().beFreeOfCycles();

    @ArchTest
    static final ArchRule modulesDoNotTouchOtherModulesInternals = classes()
            .that().resideInAPackage("com.pedeai..")
            .should(notAccessRepositoriesOrEntitiesOfOtherModules());

    private static ArchCondition<JavaClass> notAccessRepositoriesOrEntitiesOfOtherModules() {
        return new ArchCondition<>("not access repositories or entities of other modules") {
            @Override
            public void check(JavaClass origin, ConditionEvents events) {
                String originModule = moduleOf(origin);
                for (Dependency dependency : origin.getDirectDependenciesFromSelf()) {
                    JavaClass target = dependency.getTargetClass();
                    String targetModule = moduleOf(target);
                    if (originModule == null || targetModule == null || originModule.equals(targetModule)
                            || targetModule.equals("shared")) {
                        continue;
                    }
                    boolean internal = target.getPackageName().contains(".repository")
                            || target.isAnnotatedWith(Entity.class);
                    if (internal) {
                        events.add(SimpleConditionEvent.violated(dependency, dependency.getDescription()));
                    }
                }
            }
        };
    }

    private static String moduleOf(JavaClass javaClass) {
        String packageName = javaClass.getPackageName();
        if (!packageName.startsWith(ROOT)) {
            return null;
        }
        String rest = packageName.substring(ROOT.length());
        int dot = rest.indexOf('.');
        return dot < 0 ? rest : rest.substring(0, dot);
    }
}
