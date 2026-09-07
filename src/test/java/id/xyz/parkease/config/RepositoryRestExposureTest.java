package id.xyz.parkease.config;

import org.junit.jupiter.api.Test;
import org.springframework.util.ClassUtils;
import static org.junit.jupiter.api.Assertions.assertFalse;

class RepositoryRestExposureTest {

    private static final String REPOSITORY_REST_CONFIGURATION =
            "org.springframework.data.rest.webmvc.config.RepositoryRestMvcConfiguration";

    @Test
    void doesNotIncludeSpringDataRest() {
        assertFalse(ClassUtils.isPresent(REPOSITORY_REST_CONFIGURATION, getClass().getClassLoader()));
    }
}
