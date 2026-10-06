package com.github.martinfrancois;

import org.apache.logging.log4j.LogManager;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Apache POI logs through the Log4j API, log4j-to-slf4j hands those calls to SLF4J, and
 * slf4j-simple prints them. Each hop is looked up at runtime, so a release that breaks one still
 * compiles, and the application then drops every log line without an error.
 */
class LoggingBackendTest {

    @Test
    void log4jApiCallsAreRoutedToSlf4j() {
        assertThat(LogManager.getLogger(LoggingBackendTest.class).getClass().getName())
                .isEqualTo("org.apache.logging.slf4j.SLF4JLogger");
    }

    @Test
    void slf4jPrintsThroughSlf4jSimple() {
        assertThat(LoggerFactory.getILoggerFactory().getClass().getName())
                .isEqualTo("org.slf4j.simple.SimpleLoggerFactory");
    }
}
