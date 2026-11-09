package vasconcelos.volleymatch;

import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToOne;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.config.BeanDefinition;
import org.springframework.context.annotation.ClassPathScanningCandidateComponentProvider;
import org.springframework.core.type.filter.AnnotationTypeFilter;
import org.springframework.util.ClassUtils;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The native image runs Hibernate with BytecodeProvider 'none': lazy to-one associations need
 * runtime proxies and fail with "Generation of HibernateProxy instances at runtime is not allowed".
 * Lazy collections (@OneToMany / @ManyToMany) are fine since they do not use entity proxies.
 */
class EntityFetchTypeTest {

    private static final String MODEL_PACKAGE = "vasconcelos.volleymatch.model";

    @Test
    void should_have_no_lazy_to_one_association_when_scanning_entities() throws ClassNotFoundException {
        ClassPathScanningCandidateComponentProvider scanner = new ClassPathScanningCandidateComponentProvider(false);
        scanner.addIncludeFilter(new AnnotationTypeFilter(Entity.class));

        List<String> entities = new ArrayList<>();
        List<String> lazyToOne = new ArrayList<>();
        for (BeanDefinition definition : scanner.findCandidateComponents(MODEL_PACKAGE)) {
            Class<?> entity = ClassUtils.forName(definition.getBeanClassName(), getClass().getClassLoader());
            entities.add(entity.getSimpleName());
            for (Field field : entity.getDeclaredFields()) {
                ManyToOne manyToOne = field.getAnnotation(ManyToOne.class);
                OneToOne oneToOne = field.getAnnotation(OneToOne.class);
                boolean lazy = (manyToOne != null && manyToOne.fetch() == FetchType.LAZY)
                        || (oneToOne != null && oneToOne.fetch() == FetchType.LAZY);
                if (lazy) {
                    lazyToOne.add(entity.getSimpleName() + "." + field.getName());
                }
            }
        }

        assertThat(entities).contains("LiveSession");
        assertThat(lazyToOne).isEmpty();
    }
}
