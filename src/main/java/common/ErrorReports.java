package common;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collection;
import imdbupdater.Main;

public class ErrorReports {
    static Path reportRoot;

    public static void fileReport(Collection<String> nofile) throws IOException {
        Files.write(Main.PWD.resolve(reportRoot), nofile, StandardCharsets.UTF_8);
    }

}
