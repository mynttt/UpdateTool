package common;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.Collection;
import imdbupdater.Main;

public class ErrorReports {

    public static void fileReport(Collection<String> nofile, String errorFile) throws IOException {
        Files.write(Main.PWD.resolve(errorFile), nofile, StandardCharsets.UTF_8);
    }

}
