package com.github.martinfrancois.ui;

import com.github.martinfrancois.testsupport.JavaFxToolkit;
import javafx.beans.property.BooleanProperty;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.scene.Scene;
import javafx.scene.layout.StackPane;
import javafx.stage.Stage;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;

/** Drives an {@link ExportButton} in a real, headless JavaFX stage. */
class ExportButtonTest {

    private final BooleanProperty exporting = new SimpleBooleanProperty();
    private ExportButton button;
    private Stage stage;

    @BeforeAll
    static void bootToolkit() {
        JavaFxToolkit.startOnce();
    }

    @BeforeEach
    void showButton() {
        JavaFxToolkit.runAndWait(() -> {
            button = new ExportButton(exporting);
            stage = new Stage();
            Scene scene = new Scene(new StackPane(button), 300, 100);
            Styles.apply(scene);
            stage.setScene(scene);
            stage.show();
        });
    }

    @AfterEach
    void hideButton() {
        JavaFxToolkit.runAndWait(stage::hide);
    }

    @Test
    void showsOnlyItsLabelWhileNoExportRuns() {
        assertThat(button.getText()).isEqualTo(ExportButton.IDLE_TEXT);
        assertThat(button.getGraphic()).isNull();
        assertThat(button.isSpinning()).isFalse();
    }

    @Test
    void spinsAndSaysItIsExportingWhileAnExportRuns() {
        JavaFxToolkit.runAndWait(() -> exporting.set(true));

        assertThat(button.getText()).isEqualTo(ExportButton.BUSY_TEXT);
        assertThat(button.getGraphic()).isNotNull();
        assertThat(button.isSpinning()).isTrue();

        JavaFxToolkit.runAndWait(() -> exporting.set(false));

        assertThat(button.getText()).isEqualTo(ExportButton.IDLE_TEXT);
        assertThat(button.getGraphic()).isNull();
        assertThat(button.isSpinning()).isFalse();
    }

    @Test
    void aDisabledButtonFadesExceptWhileItExports() {
        JavaFxToolkit.runAndWait(() -> button.setDisable(true));
        assertThat(opacityAfterCss()).as("disabled with no export").isLessThan(1);

        JavaFxToolkit.runAndWait(() -> exporting.set(true));
        assertThat(opacityAfterCss()).as("disabled while it exports").isEqualTo(1);
    }

    @Test
    void keepsItsHeightWhileTheSpinnerShows() {
        double idleHeight = heightAfterLayout();

        JavaFxToolkit.runAndWait(() -> exporting.set(true));

        assertThat(heightAfterLayout()).isEqualTo(idleHeight);
    }

    private double opacityAfterCss() {
        AtomicReference<Double> opacity = new AtomicReference<>();
        JavaFxToolkit.runAndWait(() -> {
            stage.getScene().getRoot().applyCss();
            opacity.set(button.getOpacity());
        });
        return opacity.get();
    }

    private double heightAfterLayout() {
        AtomicReference<Double> height = new AtomicReference<>();
        JavaFxToolkit.runAndWait(() -> {
            stage.getScene().getRoot().applyCss();
            stage.getScene().getRoot().layout();
            height.set(button.getHeight());
        });
        return height.get();
    }
}
