package com.contractsys.common;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class CsvEscaperTest {
    @Test
    void escapesFormulaPrefixes() {
        assertEquals("'=1+1", CsvEscaper.escape("=1+1"));
        assertEquals("'+1", CsvEscaper.escape("+1"));
        assertEquals("'-1", CsvEscaper.escape("-1"));
        assertEquals("'@cmd", CsvEscaper.escape("@cmd"));
    }

    @Test
    void quotesCommasQuotesAndLineBreaks() {
        assertEquals("\"a,b\"", CsvEscaper.escape("a,b"));
        assertEquals("\"a\"\"b\"", CsvEscaper.escape("a\"b"));
        assertEquals("\"a\rb\"", CsvEscaper.escape("a\rb"));
    }
}
