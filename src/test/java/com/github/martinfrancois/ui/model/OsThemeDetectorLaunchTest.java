package com.github.martinfrancois.ui.model;

import com.github.martinfrancois.MainUi;
import com.github.martinfrancois.testsupport.JavaFxToolkit;
import com.jthemedetecor.OsThemeDetector;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.io.File;
import java.lang.reflect.Method;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.prefs.BackingStoreException;
import java.util.prefs.Preferences;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

/**
 * Guards the linkage between {@code com.github.Dansoftowner:jSystemThemeDetector} and
 * {@code com.github.oshi:oshi-core}, which is a runtime-only contract that no compile step
 * checks.
 *
 * <p>jSystemThemeDetector's {@code com.jthemedetecor.util.OsInfo} static initialiser calls
 * {@code oshi.SystemInfo.getCurrentPlatform()} and stores the result in a field typed
 * {@code oshi.PlatformEnum}. Nothing in this application calls that method, so the build
 * compiles happily against an oshi-core release that no longer declares it; the mismatch only
 * surfaces as a {@link NoSuchMethodError} the first time {@link OsThemeDetector} is touched.
 * {@link PresentationModel} touches it on every launch where no theme preference has been
 * stored yet, so the whole application dies on first run and {@link NoSuchMethodError} is an
 * {@link Error} that nothing catches.
 *
 * <p>Every test here therefore exercises the real detector rather than a stub, and asserts that
 * the first-launch path completes instead of throwing.
 */
class OsThemeDetectorLaunchTest {

    private static final String THEME_PREFERENCE_KEY = "themePreference";

    @BeforeAll
    static void bootToolkit() {
        // PresentationModel's first-launch branch hands the theme back through Platform.runLater.
        JavaFxToolkit.startOnce();
    }

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
    void oshiStillProvidesTheEntryPointJsystemThemeDetectorLinksAgainst() throws Exception {
        // The exact call site is `invokestatic oshi/SystemInfo.getCurrentPlatform:()Loshi/PlatformEnum;`
        // in OsInfo.<clinit>, so both halves of that descriptor have to resolve.
        Method getCurrentPlatform = Class.forName("oshi.SystemInfo").getMethod("getCurrentPlatform");

        assertThat(getCurrentPlatform.getReturnType())
                .as("the detector's field is typed oshi.PlatformEnum, not oshi.util.PlatformEnum")
                .isEqualTo(Class.forName("oshi.PlatformEnum"));
        assertThat(getCurrentPlatform.invoke(null))
                .as("oshi must be able to name the platform it is running on")
                .isNotNull();
    }

    @Test
    void initialisingOsInfoLinksAgainstTheOshiOnTheClasspath() throws Exception {
        try (URLClassLoader isolated = isolatedClasspathLoader()) {
            Class<?> osInfo = Class.forName("com.jthemedetecor.util.OsInfo", false, isolated);

            assertThatCode(() -> Class.forName("com.jthemedetecor.util.OsInfo", true, isolated))
                    .as("OsInfo.<clinit> is where the linkage breaks when oshi moves out from under it")
                    .doesNotThrowAnyException();

            // Initialisation really ran and really produced a platform, rather than being skipped.
            var platformType = osInfo.getDeclaredField("platformType");
            platformType.setAccessible(true);
            assertThat(platformType.get(null)).isNotNull();
        }
    }

    @Test
    void obtainingTheOsThemeDetectorReturnsAUsableDetector() {
        assertThatCode(OsThemeDetector::isSupported).doesNotThrowAnyException();

        OsThemeDetector detector = OsThemeDetector.getDetector();

        assertThat(detector).isNotNull();
        assertThatCode(detector::isDark)
                .as("this is the call PresentationModel makes to pick the initial theme")
                .doesNotThrowAnyException();
    }

    @Test
    void presentationModelAdoptsTheOperatingSystemThemeOnFirstLaunch() throws BackingStoreException {
        Theme expected = OsThemeDetector.getDetector().isDark() ? Theme.DARK : Theme.LIGHT;
        Preferences prefs = Preferences.userNodeForPackage(MainUi.class);
        String saved = prefs.get(THEME_PREFERENCE_KEY, null);
        prefs.remove(THEME_PREFERENCE_KEY);
        prefs.flush();
        try {
            // This is the real first-launch path of the application: no stored preference, so the
            // operating system is asked and the answer is applied on the FX application thread.
            PresentationModel model = new PresentationModel();
            JavaFxToolkit.drainFxEvents();

            assertThat(model.getTheme())
                    .as("first launch with no stored theme preference must adopt the OS theme")
                    .isEqualTo(expected);
        } finally {
            if (saved != null) {
                prefs.put(THEME_PREFERENCE_KEY, saved);
            }
            prefs.flush();
        }
    }
}
