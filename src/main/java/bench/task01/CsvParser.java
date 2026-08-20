package bench.task01;

import java.io.IOException;
import java.io.Reader;
import java.util.ArrayList;
import java.util.List;

public final class CsvParser {
    private static final char BOM = '﻿';

    public List<List<String>> parse(Reader input) throws IOException {
        List<List<String>> records = new ArrayList<>();
        ParserState state = new ParserState(input);

        // Skip UTF-8 BOM if present
        if (state.peek() == BOM) {
            state.read();
        }

        // Handle empty input
        if (state.isEof()) {
            return records;
        }

        List<String> currentRecord = new ArrayList<>();

        while (!state.isEof()) {
            String field = parseField(state);
            currentRecord.add(field);

            if (state.isEof()) {
                records.add(currentRecord);
                break;
            }

            char c = state.peek();
            if (c == ',') {
                state.read(); // consume comma
                if (state.isEof()) {
                    currentRecord.add("");
                    records.add(currentRecord);
                    break;
                }
            } else if (c == '\r' || c == '\n') {
                consumeLineBreak(state);
                records.add(currentRecord);
                currentRecord = new ArrayList<>();
            } else {
                throw new CsvFormatException(
                    "Unexpected character after field: " + c,
                    state.getLine(),
                    state.getColumn()
                );
            }
        }

        return records;
    }

    private String parseField(ParserState state) throws IOException {
        char c = state.peek();

        if (c == '"') {
            return parseQuotedField(state);
        } else {
            return parseUnquotedField(state);
        }
    }

    private String parseQuotedField(ParserState state) throws IOException {
        state.read(); // consume opening quote
        StringBuilder field = new StringBuilder();

        while (!state.isEof()) {
            char c = state.read();

            if (c == '"') {
                // Check if this is an escaped quote or the closing quote
                if (!state.isEof() && state.peek() == '"') {
                    state.read(); // consume second quote
                    field.append('"'); // add single quote to field
                } else {
                    // This is the closing quote
                    return field.toString();
                }
            } else {
                field.append(c);
            }
        }

        throw new CsvFormatException(
            "Unterminated quoted field",
            state.getLine(),
            state.getColumn()
        );
    }

    private String parseUnquotedField(ParserState state) throws IOException {
        StringBuilder field = new StringBuilder();

        while (!state.isEof()) {
            char c = state.peek();

            if (c == ',') {
                return field.toString();
            } else if (c == '\r' || c == '\n') {
                return field.toString();
            } else if (c == '"') {
                throw new CsvFormatException(
                    "Quote character in unquoted field",
                    state.getLine(),
                    state.getColumn()
                );
            } else {
                state.read();
                field.append(c);
            }
        }

        return field.toString();
    }

    private void consumeLineBreak(ParserState state) throws IOException {
        char c = state.read();

        if (c == '\r' && !state.isEof() && state.peek() == '\n') {
            state.read(); // consume LF after CR
        }
    }

    private static class ParserState {
        private final Reader reader;
        private int buffer;
        private boolean bufferValid;
        private long line;
        private long column;
        private boolean eof;

        ParserState(Reader reader) {
            this.reader = reader;
            this.line = 1;
            this.column = 1;
            this.eof = false;
            this.bufferValid = false;
        }

        char peek() throws IOException {
            if (!bufferValid && !eof) {
                buffer = reader.read();
                if (buffer == -1) {
                    eof = true;
                } else {
                    bufferValid = true;
                }
            }
            return bufferValid ? (char) buffer : '\0';
        }

        char read() throws IOException {
            if (!bufferValid && !eof) {
                buffer = reader.read();
                if (buffer == -1) {
                    eof = true;
                    return '\0';
                }
                bufferValid = true;
            }

            if (!bufferValid) {
                return '\0';
            }

            char c = (char) buffer;
            bufferValid = false;

            if (c == '\n') {
                line++;
                column = 1;
            } else {
                column++;
            }

            return c;
        }

        boolean isEof() throws IOException {
            if (!bufferValid && !eof) {
                buffer = reader.read();
                if (buffer == -1) {
                    eof = true;
                } else {
                    bufferValid = true;
                }
            }
            return eof && !bufferValid;
        }

        long getLine() {
            return line;
        }

        long getColumn() {
            return column;
        }
    }
}
