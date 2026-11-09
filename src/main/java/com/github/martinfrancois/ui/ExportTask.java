package com.github.martinfrancois.ui;

import com.github.martinfrancois.TabsExporter;
import com.github.martinfrancois.ui.model.PresentationModel;
import javafx.application.Platform;
import javafx.concurrent.Task;

import java.awt.*;
import java.io.File;

/** Runs one export off the JavaFX thread and opens the export directory when it succeeds. */
class ExportTask extends Task<Boolean> {
    private final PresentationModel model;

    ExportTask(PresentationModel model) {
        this.model = model;
    }

    @Override
    protected Boolean call() {
        boolean success = TabsExporter.export(model.getPath(), new FxThreadConsole(model));
        if (success) {
            openDirectory(new File(model.getPath()));
        }
        return success;
    }

    @Override
    protected void scheduled() {
        super.scheduled();
        Platform.runLater(model::startProgress); // Disable the button while the task is running
    }

    @Override
    protected void succeeded() {
        super.succeeded();
        Platform.runLater(model::stopProgress);
    }

    @Override
    protected void failed() {
        super.failed();
        Platform.runLater(model::stopProgress);
    }

    private static void openDirectory(File directory) {
        if (Desktop.isDesktopSupported()) {
            try {
                Desktop.getDesktop().open(directory);
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
    }
}
