package bench.task01;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

import java.io.StringReader;
import java.util.List;

public class CsvParserTest {
    private final CsvParser parser = new CsvParser();

    @Test
    void testSimpleRecord() throws Exception {
        List<List<String>> result = parser.parse(new StringReader("a,b,c"));
        assertEquals(1, result.size());
        assertEquals(List.of("a", "b", "c"), result.get(0));
    }

    @Test
    void testQuotedField() throws Exception {
        List<List<String>> result = parser.parse(new StringReader("\"hello\",world"));
        assertEquals(1, result.size());
        assertEquals(List.of("hello", "world"), result.get(0));
    }

    @Test
    void testQuotedFieldWithComma() throws Exception {
        List<List<String>> result = parser.parse(new StringReader("\"a,b\",c"));
        assertEquals(1, result.size());
        assertEquals(List.of("a,b", "c"), result.get(0));
    }

    @Test
    void testEscapedQuote() throws Exception {
        List<List<String>> result = parser.parse(new StringReader("\"a\"\"b\",c"));
        assertEquals(1, result.size());
        assertEquals(List.of("a\"b", "c"), result.get(0));
    }

    @Test
    void testEmptyField() throws Exception {
        List<List<String>> result = parser.parse(new StringReader("a,,c"));
        assertEquals(1, result.size());
        assertEquals(List.of("a", "", "c"), result.get(0));
    }

    @Test
    void testMultipleRecords() throws Exception {
        List<List<String>> result = parser.parse(new StringReader("a,b\nc,d"));
        assertEquals(2, result.size());
        assertEquals(List.of("a", "b"), result.get(0));
        assertEquals(List.of("c", "d"), result.get(1));
    }

    @Test
    void testCRLFLineEnding() throws Exception {
        List<List<String>> result = parser.parse(new StringReader("a,b\r\nc,d"));
        assertEquals(2, result.size());
        assertEquals(List.of("a", "b"), result.get(0));
        assertEquals(List.of("c", "d"), result.get(1));
    }

    @Test
    void testNewlineInQuotedField() throws Exception {
        List<List<String>> result = parser.parse(new StringReader("\"a\nb\",c"));
        assertEquals(1, result.size());
        assertEquals(List.of("a\nb", "c"), result.get(0));
    }

    @Test
    void testCRLFInQuotedField() throws Exception {
        List<List<String>> result = parser.parse(new StringReader("\"a\r\nb\",c"));
        assertEquals(1, result.size());
        assertEquals(List.of("a\r\nb", "c"), result.get(0));
    }

    @Test
    void testEmptyInput() throws Exception {
        List<List<String>> result = parser.parse(new StringReader(""));
        assertEquals(0, result.size());
    }

    @Test
    void testSingleLineBreak() throws Exception {
        List<List<String>> result = parser.parse(new StringReader("\n"));
        assertEquals(1, result.size());
        assertEquals(List.of(""), result.get(0));
    }

    @Test
    void testTrailingCommas() throws Exception {
        List<List<String>> result = parser.parse(new StringReader("a,,"));
        assertEquals(1, result.size());
        assertEquals(List.of("a", "", ""), result.get(0));
    }

    @Test
    void testEmptyQuotedField() throws Exception {
        List<List<String>> result = parser.parse(new StringReader("\"\""));
        assertEquals(1, result.size());
        assertEquals(List.of(""), result.get(0));
    }

    @Test
    void testNoFinalLinebreak() throws Exception {
        List<List<String>> result = parser.parse(new StringReader("a,b\nc,d"));
        assertEquals(2, result.size());
        assertEquals(List.of("a", "b"), result.get(0));
        assertEquals(List.of("c", "d"), result.get(1));
    }

    @Test
    void testUnterminatedQuote() {
        CsvFormatException e = assertThrows(CsvFormatException.class,
            () -> parser.parse(new StringReader("\"unterminated")));
        assertEquals("Unterminated quoted field", e.getMessage());
        assertEquals(1, e.getLine());
    }

    @Test
    void testQuoteInUnquotedField() {
        CsvFormatException e = assertThrows(CsvFormatException.class,
            () -> parser.parse(new StringReader("a\"b")));
        assertEquals("Quote character in unquoted field", e.getMessage());
        assertEquals(1, e.getLine());
    }

    @Test
    void testContentAfterClosingQuote() {
        CsvFormatException e = assertThrows(CsvFormatException.class,
            () -> parser.parse(new StringReader("\"a\"b")));
        assertEquals("Unexpected character after field: b", e.getMessage());
    }

    @Test
    void testBOMSkipping() throws Exception {
        List<List<String>> result = parser.parse(new StringReader("﻿a,b"));
        assertEquals(1, result.size());
        assertEquals(List.of("a", "b"), result.get(0));
    }

    @Test
    void testMultipleRecordsVariableFieldCount() throws Exception {
        List<List<String>> result = parser.parse(new StringReader("a,b,c\nd,e\nf"));
        assertEquals(3, result.size());
        assertEquals(List.of("a", "b", "c"), result.get(0));
        assertEquals(List.of("d", "e"), result.get(1));
        assertEquals(List.of("f"), result.get(2));
    }

    @Test
    void testComplexExample() throws Exception {
        List<List<String>> result = parser.parse(new StringReader("a,\"b,c\",d\n\"x\"\"y\",,"));
        assertEquals(2, result.size());
        assertEquals(List.of("a", "b,c", "d"), result.get(0));
        assertEquals(List.of("x\"y", "", ""), result.get(1));
    }

    @Test
    void testSpacesPreserved() throws Exception {
        List<List<String>> result = parser.parse(new StringReader(" a , b "));
        assertEquals(1, result.size());
        assertEquals(List.of(" a ", " b "), result.get(0));
    }

    @Test
    void testConsecutiveCommas() throws Exception {
        List<List<String>> result = parser.parse(new StringReader("a,,,b"));
        assertEquals(1, result.size());
        assertEquals(List.of("a", "", "", "b"), result.get(0));
    }
}
