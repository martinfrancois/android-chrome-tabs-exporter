package com.github.martinfrancois;

import com.github.martinfrancois.ui.ConsoleView;
import com.github.martinfrancois.ui.ExportButton;
import com.github.martinfrancois.ui.model.PresentationModel;
import com.github.martinfrancois.ui.model.Theme;
import javafx.application.Application;
import javafx.application.Platform;
import javafx.beans.binding.Bindings;
import javafx.concurrent.Task;
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
import javafx.scene.text.Font;
import javafx.stage.DirectoryChooser;
import javafx.stage.Stage;

import java.awt.*;
import java.io.File;


public class MainUi extends Application {
    // Define styles for dark and light theme using the enum
    private static final String DARK_STYLE = "-fx-base: #121212;";
    private static final String LIGHT_STYLE = "-fx-base: #d7d7d7;";
    // from: https://fonts.google.com/icons?selected=Material+Symbols+Outlined:light_mode:FILL@0;wght@400;GRAD@0;opsz@24&icon.query=light+mode&icon.platform=web
    public static final String ICON_CODEPOINT_LIGHT_MODE = "\ue518";
    // from: https://fonts.google.com/icons?selected=Material+Symbols+Outlined:dark_mode:FILL@0;wght@400;GRAD@0;opsz@24&icon.query=dark+mode&icon.platform=web
    public static final String ICON_CODEPOINT_DARK_MODE = "\uf036";

    private PresentationModel model;
    private Task<Boolean> backgroundTask;

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
        executeButton.setOnAction(e -> {
            backgroundTask = createBackgroundTask();
            new Thread(backgroundTask).start();
        });
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

        // Theme toggle button setup
        Button themeToggleButton = new Button();
        themeToggleButton.setStyle("-fx-background-color: transparent;");
        themeToggleButton.getStyleClass().add("material-icon-button");
        GridPane.setHalignment(themeToggleButton, HPos.RIGHT); // Align the button to the right

        // Toggle theme on button click
        themeToggleButton.setOnAction(e -> model.updatePreferredTheme(model.getTheme() == Theme.DARK ? Theme.LIGHT : Theme.DARK));

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

        // Load the CSS file
        applyStyles(scene);

        primaryStage.setScene(scene);
        primaryStage.show();

        model.themeProperty().addListener((observable, oldValue, newValue) -> applyTheme(scene, themeToggleButton, newValue));
        applyTheme(scene, themeToggleButton, model.getTheme()); // initially apply theme
    }

    /** Loads the application's font and stylesheet into scene. */
    public static void applyStyles(Scene scene) {
        Font.loadFont(MainUi.class.getResourceAsStream("MaterialSymbolsOutlined.ttf"), 24); // workaround for error: "Could not load @font-face font"
        scene.getStylesheets().add(MainUi.class.getResource("style.css").toExternalForm());
    }

    private void applyTheme(Scene scene, Button button, Theme theme) {
        if (theme == Theme.DARK) {
            scene.getRoot().setStyle(DARK_STYLE);
            button.setText(ICON_CODEPOINT_LIGHT_MODE);
        } else {
            scene.getRoot().setStyle(LIGHT_STYLE);
            button.setText(ICON_CODEPOINT_DARK_MODE);
        }
    }

    private Task<Boolean> createBackgroundTask() {
        return new Task<>() {
            @Override
            protected Boolean call() {
                boolean success = Main.export(appendText -> Platform.runLater(() -> model.appendConsoleText(appendText)), appendError -> Platform.runLater(() -> model.appendConsoleError(appendError)), model.getPath());
                if (success) {
                    openDirectory(new File(model.getPath()));
                }
                return success;
            }

            @Override
            protected void scheduled() {
                super.scheduled();
                Platform.runLater(() -> model.startProgress()); // Disable the button while the task is running
            }

            @Override
            protected void succeeded() {
                super.succeeded();
                Platform.runLater(() -> model.stopProgress());
            }

            @Override
            protected void failed() {
                super.failed();
                Platform.runLater(() -> model.stopProgress());
            }

        };
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

    private void openDirectory(File directory) {
        if (Desktop.isDesktopSupported()) {
            try {
                Desktop.getDesktop().open(directory);
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
    }

}

