package com.github.martinfrancois.testsupport;

import com.github.martinfrancois.ExportConsole;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Records what production code writes to its {@link ExportConsole} so tests can assert on the
 * messages the user would actually have seen.
 *
 * <p>This is deliberately <em>not</em> a mock of any dependency under test: it only
 * stands in for the JavaFX console that the real application wires up. Every
 * dependency the tests exercise (Apache POI, org.json, spring-web, oshi, the JDK
 * HTTP client, the real adb binary) is used for real.
 */
public final class CapturedConsole implements ExportConsole {

    private final List<String> progress = new CopyOnWriteArrayList<>();
    private final List<String> errors = new CopyOnWriteArrayList<>();

    @Override
    public void progress(String message) {
        progress.add(message);
    }

    @Override
    public void error(String message) {
        errors.add(message);
    }

    public List<String> progress() {
        return List.copyOf(progress);
    }

    public List<String> errors() {
        return List.copyOf(errors);
    }

    public boolean anyErrorContains(String needle) {
        return errors.stream().anyMatch(message -> message.contains(needle));
    }

    public boolean anyProgressContains(String needle) {
        return progress.stream().anyMatch(message -> message.contains(needle));
    }

    /** Forgets every message, for a handler that outlives one test. */
    public void clear() {
        progress.clear();
        errors.clear();
    }
}
