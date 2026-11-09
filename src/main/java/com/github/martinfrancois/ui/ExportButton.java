package com.github.martinfrancois.ui;

import javafx.animation.Animation;
import javafx.animation.Interpolator;
import javafx.animation.RotateTransition;
import javafx.beans.value.ObservableBooleanValue;
import javafx.css.PseudoClass;
import javafx.scene.Group;
import javafx.scene.control.Button;
import javafx.scene.shape.Arc;
import javafx.scene.shape.ArcType;
import javafx.scene.shape.Circle;
import javafx.scene.paint.Color;
import javafx.util.Duration;

/** Starts an export, and while one runs shows a spinner and says that it is exporting. */
public class ExportButton extends Button {
    public static final String IDLE_TEXT = "Export Android Tabs";
    public static final String BUSY_TEXT = "Exporting Android Tabs...";
    private static final PseudoClass EXPORTING = PseudoClass.getPseudoClass("exporting");
    // A ring about as tall as the button's text, so it reads as part of the label and doesn't make
    // the button taller.
    private static final double SPINNER_RADIUS = 6;
    private static final double SPINNER_STROKE_WIDTH = 2.5;

    private final Group spinner;
    private final RotateTransition spin;

    public ExportButton(ObservableBooleanValue exporting) {
        getStyleClass().add("export-button");

        Arc arc = new Arc(0, 0, SPINNER_RADIUS, SPINNER_RADIUS, 90, 270);
        arc.setType(ArcType.OPEN);
        arc.setFill(null);
        arc.setStrokeWidth(SPINNER_STROKE_WIDTH);
        arc.getStyleClass().add("export-spinner");
        // The invisible full circle keeps the spinner's bounds still while the arc turns.
        Circle bounds = new Circle(SPINNER_RADIUS + SPINNER_STROKE_WIDTH / 2, Color.TRANSPARENT);
        spinner = new Group(bounds, arc);
        spin = new RotateTransition(Duration.seconds(1), arc);
        spin.setByAngle(360);
        spin.setCycleCount(Animation.INDEFINITE);
        spin.setInterpolator(Interpolator.LINEAR);

        showExporting(exporting.get());
        exporting.addListener((observable, wasExporting, isExporting) -> showExporting(isExporting));
    }

    private void showExporting(boolean exporting) {
        setText(exporting ? BUSY_TEXT : IDLE_TEXT);
        setGraphic(exporting ? spinner : null);
        pseudoClassStateChanged(EXPORTING, exporting);
        if (exporting) {
            spin.play();
        } else {
            spin.stop();
        }
    }

    /** Whether the spinner turns, which it does only while an export runs. */
    boolean isSpinning() {
        return spin.getStatus() == Animation.Status.RUNNING;
    }
}
