package com.github.martinfrancois;

/** Receives the messages an export writes for the user: progress lines and error lines. */
public interface ExportConsole {
    void progress(String message);

    void error(String message);
}
