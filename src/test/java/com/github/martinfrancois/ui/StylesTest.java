package com.github.martinfrancois.ui;

import com.github.martinfrancois.testsupport.UtilityClasses;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class StylesTest {

    @Test
    void utilityClassCannotBeInstantiated() throws Exception {
        Throwable thrown = UtilityClasses.invokeConstructorAndCaptureThrowable(Styles.class);

        assertThat(thrown)
                .isInstanceOf(UnsupportedOperationException.class)
                .hasMessageContaining("utility class");
    }
}
