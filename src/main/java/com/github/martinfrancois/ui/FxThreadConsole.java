package com.github.martinfrancois.ui;

import com.github.martinfrancois.ExportConsole;
import com.github.martinfrancois.ui.model.PresentationModel;
import javafx.application.Platform;

/** Hands export messages to the model on the JavaFX thread, because the export runs in a background task. */
public final class FxThreadConsole implements ExportConsole {
    private final PresentationModel model;

    public FxThreadConsole(PresentationModel model) {
        this.model = model;
    }

    @Override
    public void progress(String message) {
        Platform.runLater(() -> model.appendConsoleText(message));
    }

    @Override
    public void error(String message) {
        Platform.runLater(() -> model.appendConsoleError(message));
    }
}
