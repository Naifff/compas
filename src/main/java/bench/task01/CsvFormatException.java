package bench.task01;

public class CsvFormatException extends RuntimeException {
    private final long line;
    private final long column;

    public CsvFormatException(String message, long line, long column) {
        super(message);
        this.line = line;
        this.column = column;
    }

    public long getLine() {
        return line;
    }

    public long getColumn() {
        return column;
    }
}
