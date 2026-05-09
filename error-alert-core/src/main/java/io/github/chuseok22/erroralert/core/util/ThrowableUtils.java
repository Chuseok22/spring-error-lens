package io.github.chuseok22.erroralert.core.util;

import java.io.PrintWriter;
import java.io.StringWriter;

public final class ThrowableUtils {

    private ThrowableUtils() {}

    public static String formatStackTrace(Throwable ex) {
        if (ex == null) return null;
        StringWriter sw = new StringWriter();
        ex.printStackTrace(new PrintWriter(sw));
        return sw.toString().stripTrailing();
    }
}
