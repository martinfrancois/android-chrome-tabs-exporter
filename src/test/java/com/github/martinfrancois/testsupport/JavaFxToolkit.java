package com.github.martinfrancois.testsupport;

import javafx.application.Platform;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Boots the <em>real</em> JavaFX toolkit once per JVM, using the Monocle headless glass
 * backend so it works without an X11/GTK display.
 *
 * <p>This is not a JavaFX stub: scenes, stages, properties, bindings and the FX application
 * thread are all genuine. Only the windowing backend is headless.
 */
public final class JavaFxToolkit {

    private static final AtomicBoolean STARTED = new AtomicBoolean();

    private JavaFxToolkit() {
    }

    public static void startOnce() {
        if (!STARTED.compareAndSet(false, true)) {
            return;
        }
        CountDownLatch ready = new CountDownLatch(1);
        try {
            Platform.startup(ready::countDown);
        } catch (IllegalStateException alreadyRunning) {
            ready.countDown();
        }
        await(ready, "JavaFX toolkit startup");
        // Keep the toolkit alive after the last stage is hidden so later tests can reuse it.
        runAndWait(() -> Platform.setImplicitExit(false));
    }

    /** Runs {@code action} on the FX application thread and rethrows anything it throws. */
    public static void runAndWait(Runnable action) {
        if (Platform.isFxApplicationThread()) {
            action.run();
            return;
        }
        CountDownLatch done = new CountDownLatch(1);
        AtomicReference<Throwable> failure = new AtomicReference<>();
        Platform.runLater(() -> {
            try {
                action.run();
            } catch (Throwable t) {
                failure.set(t);
            } finally {
                done.countDown();
            }
        });
        await(done, "FX action");
        Throwable thrown = failure.get();
        if (thrown instanceof RuntimeException runtimeException) {
            throw runtimeException;
        }
        if (thrown instanceof Error error) {
            throw error;
        }
        if (thrown != null) {
            throw new IllegalStateException(thrown);
        }
    }

    /** Lets every pending {@code Platform.runLater} callback drain before asserting. */
    public static void drainFxEvents() {
        runAndWait(() -> {
        });
    }

    private static void await(CountDownLatch latch, String what) {
        try {
            if (!latch.await(60, TimeUnit.SECONDS)) {
                throw new IllegalStateException("Timed out waiting for " + what);
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Interrupted waiting for " + what, e);
        }
    }
}
