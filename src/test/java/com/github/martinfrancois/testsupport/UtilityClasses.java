package com.github.martinfrancois.testsupport;

import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;

/** Helper for asserting that a utility class refuses instantiation. */
public final class UtilityClasses {

    private UtilityClasses() {
    }

    /**
     * Invokes the declared no-arg constructor of {@code type} and returns whatever it threw.
     *
     * @return the cause thrown by the constructor
     * @throws AssertionError if the constructor completed without throwing
     */
    public static Throwable invokeConstructorAndCaptureThrowable(Class<?> type) throws Exception {
        Constructor<?> constructor = type.getDeclaredConstructor();
        constructor.setAccessible(true);
        try {
            constructor.newInstance();
        } catch (InvocationTargetException e) {
            return e.getCause();
        }
        throw new AssertionError(type.getName() + " constructor did not throw");
    }
}
