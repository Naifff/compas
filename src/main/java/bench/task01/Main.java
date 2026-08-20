package bench.task01;

import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.util.List;

public class Main {
    public static void main(String[] args) {
        try {
            CsvParser parser = new CsvParser();
            InputStreamReader reader = new InputStreamReader(System.in, StandardCharsets.UTF_8);
            List<List<String>> records = parser.parse(reader);

            PrintStream out = System.out;
            for (List<String> record : records) {
                for (int i = 0; i < record.size(); i++) {
                    if (i > 0) {
                        out.print('\t');
                    }
                    out.print(record.get(i));
                }
                out.print('\n');
            }

            System.exit(0);
        } catch (CsvFormatException e) {
            System.err.printf("error: %s at %d:%d%n", e.getMessage(), e.getLine(), e.getColumn());
            System.exit(2);
        } catch (IOException e) {
            System.err.println("error: " + e.getMessage());
            System.exit(1);
        }
    }
}
