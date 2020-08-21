import static org.junit.jupiter.api.Assertions.assertEquals;
import java.io.IOException;
import java.net.URISyntaxException;
import java.nio.file.Files;
import java.nio.file.Paths;
import org.junit.jupiter.api.Test;
import updatetool.common.ExtraData;

public class URITest {

    @Test
    public void testUriEncoding() throws IOException, URISyntaxException {
        Files.lines(Paths.get(getClass().getResource("/uritestdata.txt").toURI())).forEach(l -> {
            ExtraData e = ExtraData.of(l);
            assertEquals(l, e.toURI());
        });
    }
}
