# Design Decisions

## 1. Line/Column Tracking
**Ambiguity:** Line and column numbers should be 1-based for error reporting.
**Chosen Behavior:** Line numbers start at 1, column numbers start at 1. Column numbers increment for each character, and reset to 1 at the start of each new line.
**Rationale:** This is the standard convention for text editors and error reporting tools. Matches user expectations for error location reporting.

## 2. BOM Handling
**Ambiguity:** How to handle UTF-8 BOM (Byte Order Mark).
**Chosen Behavior:** UTF-8 BOM (U+FEFF) is skipped only if it appears at the very start of the input before any other content.
**Rationale:** RFC 4180 doesn't explicitly address BOM handling, but since the spec mentions UTF-8 input and the rule says "BOM at the very start", it should only be recognized at position 0.

## 3. Empty Input Handling
**Ambiguity:** Empty input after removing BOM.
**Chosen Behavior:** Returns an empty list (0 records).
**Rationale:** No data means no records to parse.

## 4. Single Line Break
**Ambiguity:** What does a single line break represent?
**Chosen Behavior:** A single line break (either LF or CRLF) is parsed as one record containing one empty field.
**Rationale:** The field is empty because there's no content between the start of the record and the line break.

## 5. Final Record Without Line Termination
**Ambiguity:** How to handle the final record if it doesn't end with a line break.
**Chosen Behavior:** The final record is included in the output even if not terminated by CRLF or LF.
**Rationale:** RFC 4180 states "The final record may or may not be terminated by a line break", so we include it either way.

## 6. Empty Fields
**Ambiguity:** How to handle consecutive commas or trailing commas.
**Chosen Behavior:** Each comma represents a field separator. Consecutive commas create empty fields. Trailing commas create empty fields at the end.
**Rationale:** RFC 4180 explicitly allows empty fields and states they must be preserved. This is demonstrated by the example "a,,b" -> ["a", "", "b"].

## 7. Whitespace Preservation
**Ambiguity:** Should spaces around fields be trimmed?
**Chosen Behavior:** All whitespace is preserved as part of field values.
**Rationale:** RFC 4180 does not specify trimming of whitespace. This is consistent with standard CSV implementations that preserve all content.

## 8. Line Break in Quoted Fields
**Ambiguity:** What line break sequences are allowed inside quoted fields?
**Chosen Behavior:** Inside quoted fields, both LF (0x0A) and CR (0x0D) are treated as literal characters and are preserved as part of field content. CRLF sequences are also preserved.
**Rationale:** RFC 4180 Section 2 states that inside quoted fields, "the characters LF, CR, and CRLF should be interpreted literally". This allows embedding multiline content.

## 9. Quote Escaping Inside Quoted Fields
**Ambiguity:** How to handle double quotes inside quoted fields.
**Chosen Behavior:** A double-quote character (") inside a quoted field is escaped by doubling: "" represents a single quote character within the field.
**Rationale:** This is explicitly specified in RFC 4180 and the task requirements.

## 10. Content After Closing Quote
**Ambiguity:** What constitutes an error when content follows a closing quote?
**Chosen Behavior:** Any character other than comma, CR, LF, or CRLF immediately following the closing quote of a quoted field is an error.
**Rationale:** RFC 4180 specifies that the closing quote must be followed by "either a comma or end-of-line". This ensures well-formed CSV.

## 11. Quote Character in Unquoted Field
**Ambiguity:** Should a quote character appear in an unquoted field?
**Chosen Behavior:** A quote character appearing anywhere in an unquoted field is a format error.
**Rationale:** RFC 4180 doesn't allow mixing of quoted and unquoted styles within a field. If a field needs to contain quotes, it must be entirely quoted.

## 12. Parser State Machine
**Ambiguity:** How to structure the parser implementation.
**Chosen Behavior:** A single-pass recursive descent parser that:
   - Reads input character by character using a buffered reader
   - Maintains line/column tracking based on actual characters read
   - Handles quoted vs. unquoted fields explicitly
   - Validates format constraints at parse time
**Rationale:** This approach is simple, correct, and allows for precise error reporting with line/column information.

## 13. Column Number in Error Reporting
**Ambiguity:** Which column number to report when an error occurs?
**Chosen Behavior:** Report the column of the offending character or the position in the file where the error was detected.
**Rationale:** This helps users locate the exact position that caused parsing to fail.
