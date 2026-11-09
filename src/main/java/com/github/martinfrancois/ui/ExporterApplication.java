package com.github.martinfrancois.ui;

import com.github.martinfrancois.ui.model.PresentationModel;
import javafx.application.Application;
import javafx.beans.binding.Bindings;
import javafx.geometry.HPos;
import javafx.geometry.Insets;
import javafx.geometry.VPos;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.TextField;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.Priority;
import javafx.stage.DirectoryChooser;
import javafx.stage.Stage;

import java.io.File;

/** The exporter's window: the export directory, the export button, and the console. */
public class ExporterApplication extends Application {
    private PresentationModel model;
    private ExportTask backgroundTask;

    @Override
    public void start(Stage primaryStage) {
        model = new PresentationModel();

        primaryStage.setTitle("Android Chrome Tabs Exporter");

        BorderPane root = new BorderPane();

        GridPane grid = new GridPane();
        grid.setPadding(new Insets(10));
        grid.setVgap(10);
        grid.setHgap(10);

        // Directory chooser and label
        DirectoryChooser directoryChooser = new DirectoryChooser();
        directoryChooser.setTitle("Choose Save Directory");

        Label selectedDirectoryLbl = new Label("Export directory:");
        GridPane.setValignment(selectedDirectoryLbl, VPos.BOTTOM);

        TextField selectedDirectoryFld = new TextField();
        selectedDirectoryFld.setEditable(false);
        selectedDirectoryFld.textProperty().bind(model.pathProperty());
        GridPane.setHgrow(selectedDirectoryFld, Priority.ALWAYS); // Label takes up as much horizontal space as possible
        selectedDirectoryFld.setMaxWidth(Double.MAX_VALUE); // Ensure label can grow

        Button chooseDirButton = new Button("Choose Directory");
        chooseDirButton.setOnAction(e -> {
            File directory = directoryChooser.showDialog(primaryStage);
            if (directory != null) {
                // directory can be null if the user cancels the dialog
                model.updatePath(directory.getAbsolutePath());
            }
        });

        Button executeButton = new ExportButton(model.inProgressProperty());
        executeButton.setOnAction(e -> startExport());
        executeButton.disableProperty().bind(model.executableProperty().not());
        executeButton.setMaxWidth(Double.MAX_VALUE); // Allow the button to grow as wide as possible
        GridPane.setHgrow(executeButton, Priority.ALWAYS); // This makes the button grow horizontally

        // Console setup
        ConsoleView console = new ConsoleView(model.getConsoleLines());
        GridPane.setVgrow(console, Priority.ALWAYS); // Console grows vertically
        GridPane.setHgrow(console, Priority.ALWAYS); // Console grows horizontally to take up available space
        console.setMaxSize(Double.MAX_VALUE, Double.MAX_VALUE); // Allow console to grow

        Button clearButton = new Button("Clear Console");
        clearButton.setOnAction(e -> model.clearConsole());
        clearButton.disableProperty().bind(Bindings.isEmpty(model.getConsoleLines()));
        GridPane.setHalignment(clearButton, HPos.RIGHT); // Right below the console it clears

        Button themeToggleButton = new ThemeToggleButton(model);
        GridPane.setHalignment(themeToggleButton, HPos.RIGHT); // Align the button to the right

        // Adding components to grid
        grid.add(selectedDirectoryLbl, 0, 0);
        grid.add(themeToggleButton, 1, 0);
        grid.add(selectedDirectoryFld, 0, 1);
        grid.add(chooseDirButton, 1, 1);
        grid.add(executeButton, 0, 2, 2, 1); // Execute button spans two columns
        grid.add(console, 0, 3, 2, 1); // Console spans two columns and grows
        grid.add(clearButton, 1, 4);

        root.setCenter(grid);

        Scene scene = new Scene(root, 800, 450);

        Styles.apply(scene);

        primaryStage.setScene(scene);
        primaryStage.show();

        Styles.followTheme(scene, model.themeProperty());
    }

    private void startExport() {
        backgroundTask = new ExportTask(model);
        new Thread(backgroundTask).start();
    }

    /** The window's model, so tests can put the window into the state an export puts it in. */
    PresentationModel model() {
        return model;
    }

    @Override
    public void stop() throws Exception {
        super.stop();
        if (backgroundTask != null && backgroundTask.isRunning()) {
            backgroundTask.cancel();
        }
    }
}
