package com.github.martinfrancois.ui.model;

import com.github.martinfrancois.MainUi;
import com.jthemedetecor.OsThemeDetector;
import org.junit.jupiter.api.Test;

import java.io.File;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.prefs.BackingStoreException;
import java.util.prefs.Preferences;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Characterisation test for a <strong>live dependency conflict</strong> between
 * {@code com.github.Dansoftowner:jSystemThemeDetector:3.9.1} and
 * {@code com.github.oshi:oshi-core:7.4.4}.
 *
 * <p>jSystemThemeDetector's {@code com.jthemedetecor.util.OsInfo} static initialiser calls
 * {@code oshi.SystemInfo.getCurrentPlatform()} and stores the result in a field typed
 * {@code oshi.PlatformEnum}. Neither exists in oshi 7.4.4 any more: {@code SystemInfo} no
 * longer declares {@code getCurrentPlatform()}, and {@code PlatformEnum} moved to
 * {@code oshi.util.PlatformEnum} in the separate {@code oshi-common} artifact. Touching
 * {@code OsThemeDetector} therefore dies with a {@link NoSuchMethodError}.
 *
 * <p>{@link PresentationModel} touches it on <em>every launch where no theme preference has
 * been stored yet</em>, i.e. every first run on a fresh machine, and {@link NoSuchMethodError}
 * is an {@link Error} that nothing in the application catches.
 *
 * <p>These tests deliberately assert today's broken behaviour rather than the desired
 * behaviour, so the suite stays green while making the breakage impossible to overlook.
 * <strong>When the dependency conflict is resolved these tests will fail</strong> and must be
 * replaced with the positive assertions noted in the comments.
 */
class OsThemeDetectorDependencyTest {

    /**
     * A loader with no application classes in its parent, so the class initialisation attempt
     * below is always the first one and cannot observe another test's sticky failure.
     */
    private static URLClassLoader isolatedClasspathLoader() {
        URL[] classpath = Arrays.stream(System.getProperty("java.class.path").split(File.pathSeparator))
                .map(entry -> {
                    try {
                        return Path.of(entry).toUri().toURL();
                    } catch (Exception e) {
                        throw new IllegalStateException(entry, e);
                    }
                })
                .toArray(URL[]::new);
        return new URLClassLoader(classpath, ClassLoader.getPlatformClassLoader());
    }

    @Test
    void oshiNoLongerProvidesTheEntryPointJsystemThemeDetectorWasCompiledAgainst() throws Exception {
        Class<?> systemInfo = Class.forName("oshi.SystemInfo");

        assertThat(systemInfo.getMethods())
                .as("oshi.SystemInfo.getCurrentPlatform() is what jSystemThemeDetector calls")
                .noneMatch(method -> method.getName().equals("getCurrentPlatform"));

        assertThatThrownBy(() -> Class.forName("oshi.PlatformEnum"))
                .isInstanceOf(ClassNotFoundException.class);

        // It moved here, which is why the call site fails to link rather than merely misbehaving.
        assertThat(Class.forName("oshi.util.PlatformEnum").isEnum()).isTrue();
    }

    @Test
    void initialisingOsInfoFailsBecauseGetCurrentPlatformIsGone() throws Exception {
        try (URLClassLoader isolated = isolatedClasspathLoader()) {
            // Resolving without initialising succeeds; it is the static initialiser that dies.
            assertThat(Class.forName("com.jthemedetecor.util.OsInfo", false, isolated)).isNotNull();

            assertThatThrownBy(() -> Class.forName("com.jthemedetecor.util.OsInfo", true, isolated))
                    .isInstanceOf(NoSuchMethodError.class)
                    .hasMessageContaining("getCurrentPlatform");
        }
    }

    @Test
    void obtainingTheOsThemeDetectorFailsToLink() {
        // Desired behaviour once the conflict is fixed:
        //     assertThat(OsThemeDetector.getDetector()).isNotNull();
        // LinkageError rather than NoSuchMethodError specifically, because the JVM caches the
        // initialisation failure: whichever test touches OsInfo first sees NoSuchMethodError and
        // every later touch sees NoClassDefFoundError.
        assertThatThrownBy(OsThemeDetector::getDetector).isInstanceOf(LinkageError.class);
    }

    @Test
    void presentationModelCannotBeBuiltWhenNoThemePreferenceIsStored() throws BackingStoreException {
        Preferences prefs = Preferences.userNodeForPackage(MainUi.class);
        String saved = prefs.get("themePreference", null);
        prefs.remove("themePreference");
        prefs.flush();
        try {
            // This is the real first-launch path of the application. Desired behaviour once the
            // conflict is fixed: the model builds and adopts the operating system's theme.
            assertThatThrownBy(PresentationModel::new)
                    .as("first launch with no stored theme preference")
                    .isInstanceOf(LinkageError.class);
        } finally {
            if (saved != null) {
                prefs.put("themePreference", saved);
            }
            prefs.flush();
        }
    }
}
