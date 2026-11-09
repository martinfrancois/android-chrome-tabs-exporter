package com.github.martinfrancois;

import com.github.martinfrancois.ui.ExporterApplication;
import javafx.application.Application;

/** Starts JavaFX from the bundled JAR without the launcher's module-path requirement. */
public final class Launcher {
    private Launcher() {}

    public static void main(String[] args) {
        Application.launch(ExporterApplication.class, args);
    }
}
