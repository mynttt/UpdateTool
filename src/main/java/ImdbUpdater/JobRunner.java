package ImdbUpdater;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;
import org.w3c.dom.Document;
import com.google.common.collect.Lists;
import com.google.gson.Gson;
import ImdbUpdater.JobRunner.JobReport.StatusCode;
import ImdbUpdater.OMDBApi.OMDBResponse;
import ImdbUpdater.PlexDatabaseSupport.MetadataResult;
import ImdbUpdater.State.Job;
import ImdbUpdater.State.Stage;

public class JobRunner {
    private static final int LIST_PARTITIONS = 16;
    private final PlexDatabaseSupport db;
    private final ExecutorService service;
    private final String apikey;
    private final Path metadataRoot;

    public static class RatelimitException extends Exception {
        private static final long serialVersionUID = 1L;

        public RatelimitException(String msg) {
            super(msg);
        }
    }

    private class XMLWorker implements Callable<Void> {
        private final List<MetadataResult> sub;
        private final List<MetadataResult> completed = new ArrayList<>();
        private final Collection<String> nofile;
        private final DocumentBuilder builder;
        private final AtomicInteger counter;
        private final int n;

        private XMLWorker(List<MetadataResult> sub, DocumentBuilder builder, AtomicInteger counter, int n, Collection<String> nofile) {
            this.sub = sub;
            this.builder = builder;
            this.counter = counter;
            this.n = n;
            this.nofile = nofile;
        }

        @Override
        public Void call() throws Exception {
            for(var item : sub) {
                Path contents = metadataRoot.resolve(item.hash.charAt(0)+"/"+item.hash.substring(1)+".bundle/Contents");
                Path imdb = contents.resolve("com.plexapp.agents.imdb/Info.xml");
                Path combined = contents.resolve("_combined/Info.xml");
                transformXML(item, imdb, builder);
                transformXML(item, combined, builder);
                Utility.printStatusBar(counter.getAndIncrement(), n, 15);
                completed.add(item);
            }
            return null;
        }

        private void transformXML(MetadataResult item, Path p, DocumentBuilder builder) throws Exception {
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

    private static class OMDBWorker implements Callable<Void> {
        private final List<MetadataResult> sub;
        private final Map<String, OMDBResponse> map = new HashMap<>();
        private final Gson gson;
        private final OMDBApi api;
        private final AtomicInteger counter;
        private final int n;

        private OMDBWorker(List<MetadataResult> sub, Gson gson, OMDBApi omdbApi, AtomicInteger counter, int n) {
            this.sub = sub;
            this.gson = gson;
            this.api = omdbApi;
            this.counter = counter;
            this.n = n;
        }

        @Override
        public Void call() throws Exception {
            for(var item : sub) {

                var response = api.queryId(item.imdbId);

                if(response.statusCode() == 401)
                    throw new RatelimitException("We are being rate limited. Try again in 24h or buy their premium option. Will abort now!");

                if(response.statusCode() != 200) {
                    System.err.println(response.statusCode());
                    System.err.println(response.body());
                    throw new RuntimeException("Unexpected request behavior: \nCode:\n" + response.statusCode() + "\nPayload:\n" + response.body());
                }

                OMDBResponse result = gson.fromJson(response.body(), OMDBResponse.class);

                if(result.Response) {
                    Utility.printStatusBar(counter.getAndIncrement(), n, 15);
                    map.put(item.imdbId, result);
                    continue;
                }

                throw new IllegalStateException("API returned failed: " + response.body());
            }
            return null;
        }

    }

    public static class JobReport {
        public final StatusCode code;
        public final Job progress;
        public final Throwable exception;
        public enum StatusCode { PASS, ERROR, RATE_LIMIT }

        private JobReport(StatusCode code, Job progress, Throwable ex) {
            this.code = code;
            this.progress = progress;
            this.exception = ex;
        }
    }

    public JobRunner(PlexDatabaseSupport db, ExecutorService service, String apikey, Path metadataRoot) {
        this.db = db;
        this.service = service;
        this.apikey = apikey;
        this.metadataRoot = metadataRoot;
    }

    public JobReport run(Job job) {
        Objects.requireNonNull(job);

        while(job.stage != Stage.COMPLETED) {
            try {
                jobInternal(job);
            } catch(Throwable t) {
                if(t instanceof RatelimitException)
                    return new JobReport(StatusCode.RATE_LIMIT, job, null);
                return new JobReport(StatusCode.ERROR, job, t);
            }
        }

        return new JobReport(StatusCode.PASS, job, null);
    }

    private void jobInternal(Job job) throws Throwable {
        switch (job.stage) {
        case ACCUMULATED_META:
            transformMeta(job);
            break;
        case TRANSFORMED_META:
            updateDb(job);
            break;
        case ANALYSED_DB:
            accumulateMeta(job);
            break;
        case CREATED:
            analyseDb(job);
            break;
        case DB_UPDATED:
            if(job.dbmode) {
                job.stage = Stage.COMPLETED;
                job.touch();
                return;
            }
            updateXml(job);
            break;
        default:
            throw new RuntimeException("Invalid stage: " + job.stage);
        }
    }

    private void analyseDb(Job job) {
        System.out.println("1. Analyzing Database");
        var items = db.requestEntries(db.requestLibraryIdOfUuid(job.uuid));
        int skipped = 0;
        for(var it = items.iterator(); it.hasNext(); ) {
            var item = it.next();
            if(item.imdbId == null) {
                if(skipped == 0)
                    System.out.println("\nDetected items with invalid guid. These items will be ignored as they do not provide IMDB IDs.\n");
                skipped++;
                System.out.println("Item: " + item.title + " has non matching imdb guid: " + item.guid);
                it.remove();
            }
        }
        System.out.println("\nFiltered " + skipped + " invalid item(s).");
        System.out.println();
        job.stage = Stage.ANALYSED_DB;
        job.items = items;
        job.touch();
    }

    private void accumulateMeta(Job job) throws Throwable {
        System.out.println("2. Accumulating Metadata\n");
        System.out.println("Items: " + job.items.size());
        var unprocessed = job.items.stream().filter(j -> !job.responses.containsKey(j.imdbId)).collect(Collectors.toList());
        System.out.println("Metadata missing for: " + unprocessed.size());
        System.out.println("\nRetrieving metadata...");
        int n = unprocessed.size()/LIST_PARTITIONS;
        var sublists = Lists.partition(unprocessed, n == 0 ? 1 : n);
        Gson gson = new Gson();
        HashMap<Future<Void>, OMDBWorker> map = new HashMap<>();
        boolean rateLimited = false;
        Throwable ex = null;
        AtomicInteger counter = new AtomicInteger();

        for(var sub : sublists) {
            OMDBWorker w = new OMDBWorker(sub, gson, new OMDBApi(apikey), counter, unprocessed.size());
            map.put(service.submit(w), w);
        }

        for(var entry : map.entrySet()) {
            try {
                entry.getKey().get();
            } catch(ExecutionException e) {
                Throwable t = e;
                while(t.getCause() != null)
                    t = t.getCause();
                if(t instanceof RatelimitException) {
                    if(!rateLimited)
                        System.err.println(t.getMessage());
                    rateLimited = true;
                    continue;
                }
                ex = e.getCause();
            }
            job.responses.putAll(map.get(entry.getKey()).map);
        }

        if(rateLimited || ex != null)
            System.out.println('\n');

        if(rateLimited)
            throw new RatelimitException("");

        if(ex != null)
            throw ex;

        job.stage = Stage.ACCUMULATED_META;
        job.touch();
        System.out.println("\n\nFetched all metadata from OMDb.");
        System.out.println();
    }

    private void transformMeta(Job job) {
        System.out.println("3. Transforming metadata\n");
        var map = new HashMap<MetadataResult, OMDBResponse>();
        job.items.forEach(i -> map.put(i, job.responses.get(i.imdbId)));
        var noUpdate = map.entrySet().stream().filter(Utility::needsNoUpdate).collect(Collectors.toSet());
        if(!noUpdate.isEmpty()) {
            System.out.println(noUpdate.size() + " item(s) need no update.");
            for(var item : noUpdate) {
                map.remove(item.getKey());
                job.items.remove(item.getKey());
                job.responses.remove(item.getKey().imdbId);
            }
        }
        System.out.println("Transforming " + map.size() + " item(s)\n");
        map.entrySet().stream().forEach(Utility::updateMetadata);
        System.out.println("\nTransformed entries for " + map.size() + " items(s).");
        System.out.println();
        job.responses.clear();
        job.touch();
        job.stage = Stage.TRANSFORMED_META;
    }

    private void updateDb(Job job) {
        System.out.println("4. Updating Database\n");
        System.out.println("Updating " + job.items.size() + " via batch request...");
        db.requestBatchUpdateOf(job.items);
        System.out.println("Batch request finished successfully. Database is now up to date!");
        System.out.println();
        job.touch();
        job.stage = Stage.DB_UPDATED;
    }

    private void updateXml(Job job) throws Throwable {
        System.out.println("5. Updating XML\n");
        System.out.println("Updating XML fallback files for " + job.items.size() + " item(s).");
        System.out.println();
        int n = job.items.size()/LIST_PARTITIONS;
        var sublists = Lists.partition(job.items, n == 0 ? 1 : n);
        var factory = DocumentBuilderFactory.newInstance();
        AtomicInteger counter = new AtomicInteger();
        HashMap<Future<Void>, XMLWorker> map = new HashMap<>();
        var nofile = Collections.synchronizedCollection(new ArrayList<String>());
        for(var sub : sublists) {
            var worker = new XMLWorker(sub, factory.newDocumentBuilder(), counter, job.items.size(), nofile);
            map.put(service.submit(worker), worker);
        }
        Throwable t = null;
        List<List<MetadataResult>> cleanup = new ArrayList<>();
        for(var entry : map.entrySet()) {
            try {
                entry.getKey().get();
            } catch(ExecutionException e) {
                t = e.getCause();
            }
            cleanup.add(entry.getValue().completed);
        }
        for(var c : cleanup)
            job.items.removeAll(c);
        if(nofile.size()>0) {
            String errorFile = "xml-error-" + System.currentTimeMillis() + ".log";
            System.out.println("\n\n" +  nofile.size() + " XML file(s) have failed to be updated due to them not being present on the file system.");
            System.out.println("This is not an issue as they're not important for Plex as it reads the ratings from the database.");
            System.out.println("The files have been dumped as " + errorFile + " in the PWD.");
            Files.write(Main.PWD.resolve(errorFile), nofile, StandardCharsets.UTF_8);
        } else {
            System.out.println();
        }
        if(t != null)
            throw t;
        System.out.println("\nCompleted updating of XML fallback files.\n");
        job.touch();
        job.stage = Stage.COMPLETED;
    }
}
