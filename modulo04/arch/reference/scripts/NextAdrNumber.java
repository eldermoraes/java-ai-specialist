import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

/**
 * Prints the next free ADR number, four digits, for the ADR folder given as the argument.
 *
 * <p>Usage: {@code java NextAdrNumber.java [adr-folder]} (default folder: {@code docs/adr}).
 * ADR files are named {@code NNNN-slug.md}. A missing or empty folder prints {@code 0001}.
 */
public class NextAdrNumber {

    private static final Pattern ADR_FILE = Pattern.compile("^(\\d{4})-.+\\.md$");

    public static void main(String[] args) throws IOException {
        Path folder = Path.of(args.length > 0 ? args[0] : "docs/adr");
        int highest = 0;
        if (Files.isDirectory(folder)) {
            try (Stream<Path> files = Files.list(folder)) {
                highest = files
                        .map(file -> ADR_FILE.matcher(file.getFileName().toString()))
                        .filter(Matcher::matches)
                        .mapToInt(matcher -> Integer.parseInt(matcher.group(1)))
                        .max()
                        .orElse(0);
            }
        }
        System.out.printf("%04d%n", highest + 1);
    }
}
