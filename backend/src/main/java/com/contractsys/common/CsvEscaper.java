package com.contractsys.common;

public final class CsvEscaper {
    private CsvEscaper() {
    }

    public static String escape(String value) {
        if (value == null) {
            return "";
        }
        String safeValue = startsWithFormulaPrefix(value) ? "'" + value : value;
        if (safeValue.contains(",") || safeValue.contains("\"")
                || safeValue.contains("\n") || safeValue.contains("\r")) {
            return "\"" + safeValue.replace("\"", "\"\"") + "\"";
        }
        return safeValue;
    }

    private static boolean startsWithFormulaPrefix(String value) {
        if (value.isEmpty()) {
            return false;
        }
        return switch (value.charAt(0)) {
            case '=', '+', '-', '@' -> true;
            default -> false;
        };
    }
}
