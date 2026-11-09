package com.github.martinfrancois.ui;

import com.github.martinfrancois.testsupport.JavaFxToolkit;
import com.github.martinfrancois.ui.model.ConsoleLine;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.scene.Scene;
import javafx.scene.control.skin.VirtualFlow;
import javafx.stage.Stage;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;

/** Drives a {@link ConsoleView} in a real, headless JavaFX stage. */
class ConsoleViewTest {

    private ObservableList<ConsoleLine> lines;
    private ConsoleView console;
    private Stage stage;

    @BeforeAll
    static void bootToolkit() {
        JavaFxToolkit.startOnce();
    }

    @BeforeEach
    void showConsole() {
        JavaFxToolkit.runAndWait(() -> {
            lines = FXCollections.observableArrayList();
            console = new ConsoleView(lines);
            stage = new Stage();
            Scene scene = new Scene(console, 300, 100);
            // Without the application's font, a host with no fonts installed has none to lay the
            // lines out with.
            Styles.apply(scene);
            stage.setScene(scene);
            stage.show();
            layOut();
        });
    }

    @AfterEach
    void hideConsole() {
        JavaFxToolkit.runAndWait(stage::hide);
    }

    @Test
    void newLinesScrollTheConsoleToTheNewestLine() {
        JavaFxToolkit.runAndWait(() -> addLines(0, 50));
        assertThat(lastVisibleIndexAfterLayout()).isEqualTo(49);

        JavaFxToolkit.runAndWait(() -> addLines(50, 60));
        assertThat(lastVisibleIndexAfterLayout()).isEqualTo(59);
    }

    private void addLines(int from, int to) {
        lines.addAll(IntStream.range(from, to).mapToObj(i -> new ConsoleLine("line " + i, false)).toList());
    }

    /**
     * Lays the console out and reads the last row on screen from the skin's virtual flow, because
     * ListView itself doesn't say which rows are visible.
     */
    private int lastVisibleIndexAfterLayout() {
        AtomicInteger index = new AtomicInteger(-1);
        JavaFxToolkit.runAndWait(() -> {
            layOut();
            index.set(((VirtualFlow<?>) console.lookup(".virtual-flow")).getLastVisibleCell().getIndex());
        });
        return index.get();
    }

    /**
     * Lays the console out the way two pulses would. The first pass of a fresh list only
     * measures its cells, so the visible cells exist after the second.
     */
    private void layOut() {
        console.applyCss();
        console.layout();
        console.layout();
    }
}
