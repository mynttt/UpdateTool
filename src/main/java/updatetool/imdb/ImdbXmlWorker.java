package updatetool.imdb;

import java.nio.file.Files;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.atomic.AtomicInteger;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;
import org.tinylog.Logger;
import org.w3c.dom.Document;
import updatetool.common.Utility;
import updatetool.imdb.ImdbDatabaseSupport.ImdbMetadataResult;

class ImdbXmlWorker implements Callable<Void> {
    private final List<ImdbMetadataResult> sub;
    private final Collection<String> nofile;
    private final DocumentBuilder builder;
    private final AtomicInteger counter;
    private final Path metadataRoot;
    private final int n;
    final List<ImdbMetadataResult> completed = new ArrayList<>();

    ImdbXmlWorker(List<ImdbMetadataResult> sub, DocumentBuilder builder, AtomicInteger counter, int n, Collection<String> nofile, Path metadataRoot) {
        this.sub = sub;
        this.builder = builder;
        this.counter = counter;
        this.n = n;
        this.nofile = nofile;
        this.metadataRoot = metadataRoot;
    }

    @Override
    public Void call() throws Exception {
        for(var item : sub) {
            Path contents = metadataRoot.resolve(item.hash.charAt(0)+"/"+item.hash.substring(1)+".bundle/Contents");
            Path imdb = contents.resolve("com.plexapp.agents.imdb/Info.xml");
            Path combined = contents.resolve("_combined/Info.xml");
            try {
                transformXML(item, imdb, builder);
                transformXML(item, combined, builder);
            } catch(Exception e) {
                Logger.info("Uncaught exception @ XML Worker: Continuing... ({})", e.getClass().getSimpleName());
            }
            int c = counter.incrementAndGet();
            if(c % 100 == 0)
                Logger.info("Transforming [{}/{}]...", c, n);
            completed.add(item);
        }
        return null;
    }

    private void transformXML(ImdbMetadataResult item, Path p, DocumentBuilder builder) throws Exception {
        Document document;
        try(var stream = Files.newInputStream(p)) {
            document = builder.parse(stream);
        } catch (NoSuchFileException e) {
            nofile.add(p.toAbsolutePath().toString());
            return;
        }
        var children = document.getDocumentElement().getChildNodes();
        for(int i = 0; i < children.getLength(); i++) {
            if(children.item(i).getNodeName().equals("rating"))
                children.item(i).setTextContent(Utility.doubleToOneDecimalString(item.rating));
            if(children.item(i).getNodeName().equals("rating_image"))
                children.item(i).setTextContent("imdb://image.rating");
        }
        Transformer transformer = TransformerFactory.newInstance().newTransformer();
        try(var stream = Files.newOutputStream(p)) {
            transformer.transform(new DOMSource(document), new StreamResult(Files.newOutputStream(p)));
        }
    }
}
