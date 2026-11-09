package com.github.martinfrancois.ui;

import com.github.martinfrancois.ui.model.ConsoleLine;
import javafx.application.Platform;
import javafx.collections.ListChangeListener;
import javafx.collections.ObservableList;
import javafx.css.PseudoClass;
import javafx.scene.control.ContextMenu;
import javafx.scene.control.ListCell;
import javafx.scene.control.ListView;
import javafx.scene.control.MenuItem;
import javafx.scene.control.SelectionMode;
import javafx.scene.input.Clipboard;
import javafx.scene.input.ClipboardContent;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyCodeCombination;
import javafx.scene.input.KeyCombination;
import javafx.scene.input.KeyEvent;

import java.util.List;

/**
 * Shows the console lines, with errors in red. A text area can't color single lines, so the
 * console is a list of lines, and it copies whole selected lines to the clipboard.
 */
public class ConsoleView extends ListView<ConsoleLine> {
    private static final PseudoClass ERROR = PseudoClass.getPseudoClass("error");
    private static final KeyCombination COPY = new KeyCodeCombination(KeyCode.C, KeyCombination.SHORTCUT_DOWN);

    public ConsoleView(ObservableList<ConsoleLine> lines) {
        super(lines);
        getStyleClass().add("console");
        getSelectionModel().setSelectionMode(SelectionMode.MULTIPLE);
        setCellFactory(view -> new LineCell());

        MenuItem copy = new MenuItem("Copy");
        copy.setOnAction(event -> copySelection());
        setContextMenu(new ContextMenu(copy));
        addEventHandler(KeyEvent.KEY_PRESSED, this::copyOnShortcut);
        getItems().addListener(this::showNewestLine);
    }

    /**
     * Scrolls to the newest line whenever lines are added, like a terminal. The scroll runs after
     * this change notification ends: this listener is registered before the skin's, so during the
     * notification the skin still counts the old lines and a scroll stops at the old last line.
     */
    private void showNewestLine(ListChangeListener.Change<? extends ConsoleLine> change) {
        while (change.next()) {
            if (change.wasAdded()) {
                Platform.runLater(this::scrollToNewestLine);
                return;
            }
        }
    }

    /**
     * Scrolls to the end of the list. An index past the last line makes the list jump to its end,
     * while the last line's own index places that line at the top, which the list estimates from
     * row heights it hasn't measured yet and can leave the line just below the visible area.
     */
    private void scrollToNewestLine() {
        scrollTo(getItems().size());
    }

    private void copyOnShortcut(KeyEvent event) {
        if (COPY.match(event)) {
            copySelection();
            event.consume();
        }
    }

    /** Puts the selected lines on the clipboard in console order, one per line. */
    void copySelection() {
        List<String> selected = getSelectionModel().getSelectedIndices().stream()
                .sorted()
                .map(index -> getItems().get(index).text())
                .toList();
        if (selected.isEmpty()) {
            return;
        }
        ClipboardContent content = new ClipboardContent();
        content.putString(String.join("\n", selected));
        Clipboard.getSystemClipboard().setContent(content);
    }

    private static final class LineCell extends ListCell<ConsoleLine> {
        LineCell() {
            setWrapText(true);
            // Without this the cell asks for its whole line's width, so long lines don't wrap.
            setPrefWidth(0);
        }

        @Override
        protected void updateItem(ConsoleLine line, boolean empty) {
            super.updateItem(line, empty);
            boolean present = !empty && line != null;
            setText(present ? line.text() : null);
            pseudoClassStateChanged(ERROR, present && line.error());
        }
    }
}
