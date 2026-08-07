package com.github.martinfrancois.ui.model;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ThemeTest {

    @Test
    void exposesExactlyTheDarkAndLightConstants() {
        // PresentationModel persists Theme.name() into java.util.prefs and reads it back with
        // Theme.valueOf(), so renaming or reordering a constant silently breaks stored preferences.
        assertThat(Theme.values()).containsExactly(Theme.DARK, Theme.LIGHT);
    }

    @ParameterizedTest
    @EnumSource(Theme.class)
    void nameRoundTripsThroughValueOfAsThePreferenceStoreRequires(Theme theme) {
        assertThat(Theme.valueOf(theme.name())).isSameAs(theme);
    }

    @Test
    void valueOfRejectsUnknownStoredPreference() {
        assertThatThrownBy(() -> Theme.valueOf("SEPIA")).isInstanceOf(IllegalArgumentException.class);
    }
}
