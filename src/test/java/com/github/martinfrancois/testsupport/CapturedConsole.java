package com.github.martinfrancois.testsupport;

import com.github.martinfrancois.Main;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Consumer;

/**
 * Captures what production code pushes into the static {@link Main#updateProgress} /
 * {@link Main#updateError} consumers so tests can assert on the messages the user
 * would actually have seen.
 *
 * <p>This is deliberately <em>not</em> a mock of any dependency under test: it only
 * stands in for the JavaFX text area that the real application wires up. Every
 * dependency the tests exercise (Apache POI, org.json, spring-web, oshi, the JDK
 * HTTP client, the real adb binary) is used for real.
 */
public final class CapturedConsole implements AutoCloseable {

    private final List<String> progress = new CopyOnWriteArrayList<>();
    private final List<String> errors = new CopyOnWriteArrayList<>();
    private final Consumer<String> previousProgress;
    private final Consumer<String> previousError;

    public CapturedConsole() {
        previousProgress = Main.updateProgress;
        previousError = Main.updateError;
        Main.updateProgress = progress::add;
        Main.updateError = errors::add;
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

    @Override
    public void close() {
        Main.updateProgress = previousProgress;
        Main.updateError = previousError;
    }
}
