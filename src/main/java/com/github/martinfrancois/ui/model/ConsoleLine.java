package com.github.martinfrancois.ui.model;

/** One entry of the console, as written by the export. */
public record ConsoleLine(String text, boolean error) {
}
