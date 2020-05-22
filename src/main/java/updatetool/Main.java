package updatetool;

import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.nio.charset.StandardCharsets;
import java.nio.file.FileSystem;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;
import org.tinylog.Logger;
import com.google.common.util.concurrent.ThreadFactoryBuilder;
import de.mynttt.ezconf.ConfigurationValidator;
import de.mynttt.ezconf.ConfigurationValidator.Validate;
import de.mynttt.ezconf.ConfigurationValidator.ValidationContext;
import de.mynttt.ezconf.EzConf;
import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import updatetool.api.Implementation;
import updatetool.common.AbstractApi;
import updatetool.common.TmdbApi;
import updatetool.common.TvdbApi;
import updatetool.common.Utility;
import updatetool.exceptions.ApiCallFailedException;

public class Main {
    public static final Path PWD = Paths.get(".");
    public static final ExecutorService EXECUTOR = Executors.newFixedThreadPool(5, new ThreadFactoryBuilder().setDaemon(true).build());
    
    private static final Map<String, Implementation> IMPLEMENTATIONS = new HashMap<>();
    private static final Scheduler SCHEDULER = new Scheduler();
    private static final Validate<String> _V = s -> s == null || s.isBlank() ? "parameter must not be null/empty" : null;
    private static final ValidationContext VAL_CTX = ConfigurationValidator.newInstance()
            .valuesMatchInGroup(_V, "meta", "id", "desc", "usage", "help", "entry")
            .build();

    @SuppressFBWarnings({"OBL_UNSATISFIED_OBLIGATION", "OS_OPEN_STREAM"})
    private static String loadVersion() {
        try {
            return new String(Main.class.getResourceAsStream("/VERSION").readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            return "FAILED_TO_READ_VERSION";
        }
    }
    
    public static final String VERSION = loadVersion();

    public static void main(String[] args) throws Exception {
        preLogPurge();
        Class.forName("org.sqlite.JDBC");
        
        Thread.setDefaultUncaughtExceptionHandler((t, e) -> {
            Logger.error("Uncaught " + e.getClass().getSimpleName() + " exception encountered...");
            Logger.error("Please contact the maintainer of the application with the stacktrace below if you think this is unwanted behavior.");
            Logger.error("========================================");
            Logger.error(e);
            Logger.error("========================================");
            Logger.error("The application will terminate now. (Version: " + VERSION + ")");
            System.exit(-1);
        });
        
        loadImplementations();
        var parsedArgs = processArgs(args);
        
        if(parsedArgs.isEmpty()) {
            printHelp();
            System.exit(-1);
        }
        
        Logger.info("Running version: {}", VERSION);
        Logger.info("Args: {}", parsedArgs);
        
        for(var impl : IMPLEMENTATIONS.values()) {
            var implArgs = parsedArgs.get(impl.id);
            Logger.info("<< INIT: {} @ {} >>", impl.id, implArgs);
            impl.bootstrap(implArgs);
            Logger.info("<< INIT SUCCESS >>");
            SCHEDULER.prepare(impl);
        }
        
        SCHEDULER.go();
    }
    
    private static void loadImplementations() throws URISyntaxException, IOException {
        URI uri = Main.class.getResource("/desc").toURI();
        Path myPath;
        if (uri.getScheme().equals("jar")) {
            FileSystem fileSystem = FileSystems.newFileSystem(uri, Collections.emptyMap());
            myPath = fileSystem.getPath("/desc");
        } else {
            myPath = Paths.get(uri);
        }
        Files.walk(myPath, 1).filter(p -> p.getFileName().toString().endsWith(".ez")).forEach(Main::loadImplementation);
    }
    
    private static void loadImplementation(Path p) {
        try {
            var c = EzConf.defaultParser().parse(p);
            var r = VAL_CTX.validate(c);
            if(!r.isValid())
                throw new RuntimeException("Invalid Configuration @ " + p.toAbsolutePath() + ": " + r.getIssues());
            var g = c.getGroup("meta");
            String id = g.getValue("id"),
                   desc = g.getValue("desc"),
                   help = g.getValue("help"),
                   usage = g.getValue("usage");
            Class<?> impl = Class.forName(g.getValue("entry"));
            var constructor = impl.getConstructor(String.class, String.class, String.class, String.class);
            Implementation i = (Implementation) constructor.newInstance(id, desc, usage, help);
            if(IMPLEMENTATIONS.containsKey(id))
                throw new RuntimeException("ID clash @ " + id + " | " + IMPLEMENTATIONS.get(id).getClass().getCanonicalName() + " <> " + i.getClass().getCanonicalName());
            IMPLEMENTATIONS.put(id, i);
            Logger.info("Loaded implementation << {} << {}", id, i.getClass().getCanonicalName());
        } catch (Exception e) {
            throw Utility.rethrow(e);
        }
    }
    
    private static Map<String, Map<String, String>> processArgs(String[] args) {
        Map<String, Map<String, String>> map = new HashMap<>();
        if(args.length % 2 != 0)
            throw new IllegalArgumentException("Invalid arguments: " + Arrays.toString(args) + " | Arguments must be an even number (currently: " + args.length + ") (id + args for implementation) * called tools. Look at the help by running this tool without arguments for an example.");
        for(int i = 0; i < args.length; i+=2) {
            String id = args[i];
            String rargs = args[i+1];
            if(map.containsKey(id))
                throw new IllegalArgumentException("ID clash: already parsed args for id: " + id);
            if(!IMPLEMENTATIONS.containsKey(id))
                throw new IllegalArgumentException("Invalid ID: " + id + ". No tool associated. Check help for listed tools (run this without args).");
            Map<String, String> mapped = new HashMap<>();
            map.put(id, mapped);
            if(!rargs.startsWith("{") && !rargs.endsWith("}"))
                throw new IllegalArgumentException("Arguments must be enclosed in curly-brackets like this {arg=var,arg2=var2}. (Got: " + rargs + ")");
            String[] split = rargs.substring(1, rargs.length()-1).split(",", -1);
            if(split.length == 1 && split[0].isBlank())
                continue;
            for(int kv = 0; kv < split.length; kv+=2) {
                String[] kvs = split[kv].split("=");
                if(kvs.length %2 != 0)
                    throw new IllegalArgumentException("Arguments must be an even number (k+v) = 2 | (currently: " + kvs.length + " @ " + Arrays.toString(kvs) + ")");
                mapped.put(kvs[0], kvs[1]);
            }
        }
        return map;
    }

    private static void preLogPurge() throws IOException {
        var files = Files.list(Main.PWD)
            .filter(p -> p.getFileName().toString().startsWith("updatetool."))
            .collect(Collectors.toList());
        for(var p : files)
            Files.delete(p);
    }
    
    public static void printHelp() {
        System.out.println();
        System.out.println("UpdateTool - v: " + VERSION);
        System.out.println("Contact: https://github.com/mynttt/UpdateTool");
        System.out.println("");
        System.out.println("Invoke tools: [[tool-id] {args}]+");
        System.out.println("Example for multiple tools: imdb-docker {invoke=12} age-rating {invoke=12,config=rating.ez}");
        System.out.println();
        List<Map.Entry<String, Implementation>> sorted = new ArrayList<>(IMPLEMENTATIONS.entrySet());
        sorted.sort((c1, c2) -> c1.getKey().compareToIgnoreCase(c2.getKey()));
        sorted.forEach(v -> {
            var i = v.getValue();
            System.out.println("==> Tool     : " + i.id);
            System.out.println("> Description: " + i.desc);
            System.out.println("> Usage      : " + i.usage);
            System.out.println(i.help);
            System.out.println("\n");
        });
    }

    static void rollingLogPurge() throws IOException {
        var files = Files.list(Main.PWD)
                .filter(p -> p.getFileName().toString().startsWith("updatetool.")).collect(Collectors.toList());
        var keep = files.stream().max((p1, p2) -> {
            try {
                return Files.getLastModifiedTime(p2).compareTo(Files.getLastModifiedTime(p1));
            } catch (IOException e) {}
            return 0;
        });
        if(files.size() > 1) {
            keep.ifPresent(f -> files.remove(f));
            for(var f : files)
                Files.delete(f);
        }
    }
    
    public static void testApiTmdb(String apikeyTmdb) throws Exception {
        Logger.info("Testing TMDB API key: " + apikeyTmdb);
        var api = new TmdbApi(apikeyTmdb);
        genericApiTest(api);
    }
    
    public static void testApiTvdb(String key) {
        Logger.info("Testing TVDB API authorization: apikey={}", key);
        try {
            new TvdbApi(key);
        } catch(ApiCallFailedException e) {
            Logger.error("API Test failed: " + e.getMessage());
            Logger.error("Keys available under: https://thetvdb.com/");
            System.exit(-1);
        }
        Logger.info("Test passed. API Key is valid.");
    }

    private static void genericApiTest(AbstractApi api) throws Exception {
        var response = api.testApi();
        if(response.statusCode() != 200) {
            Logger.error("API Test failed: Code " + response.statusCode());
            Logger.error("Payload:" + response.body());
            Logger.error("Key available under:" + api.keysWhere());
            System.exit(-1);
        }
        Logger.info("Test passed. API Key is valid.");
    }
}

class Scheduler {
    private final ScheduledExecutorService service = Executors.newSingleThreadScheduledExecutor();
    private final Map<String, Runnable> jobs = new HashMap<>();
    private final Map<String, Integer> interval = new HashMap<>();
    private final List<String> runAtStartup = new ArrayList<>();

    public void prepare(Implementation impl) {
        jobs.put(impl.id, new TaskWrapper(impl.id, impl.entryPoint()));
        interval.put(impl.id, impl.scheduleEveryHours());
        runAtStartup.add(impl.id);
    }

    public void go() {
        Logger.info("Scheduler is loading tasks... Blocking until completely set-up and ready to go.");
        var blocker = new Lock();
        service.submit(blocker);
        Logger.info("Scheduling tasks...");
        jobs.forEach((k, v) -> {
            int run = interval.get(k);
            service.scheduleAtFixedRate(v, 1, run, TimeUnit.HOURS);
            Logger.info("Scheduled {} task to run @ every {} hour(s).", k, run);
        });
        
        runAtStartup.forEach(k -> {
            service.submit(jobs.get(k));
            Logger.info("Queued task {} for immediate execution.", k);
        });
        Logger.info("Unblocking scheduler...");
        blocker.unblock();
        Logger.info("Running supplied tasks immediately NOW!");
    }
}

class Lock implements Runnable {

    @Override
    @SuppressFBWarnings({"UW_UNCOND_WAIT", "WA_NOT_IN_LOOP"})
    public void run() {
        synchronized (this) {
            try {
                wait();
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        }
    }
    
    @SuppressFBWarnings("NN_NAKED_NOTIFY")
    public void unblock() {
        synchronized (this) {
            notify();
        }
    }
}

class TaskWrapper implements Runnable {
    private int executionCount;
    private final String id;
    private final Runnable implementation;
    
    public TaskWrapper(String id, Runnable implementation) {
        this.id = id;
        this.implementation = implementation;
    }

    @Override
    @SuppressFBWarnings("DM_GC")
    public void run() {
        Logger.info("================================================");
        Logger.info("Starting task: {} | Execution count: {}", id, executionCount++);
        Logger.info("================================================");
        try { Main.rollingLogPurge(); } catch (IOException e) { Logger.error(e); }
        var start = Instant.now();
        implementation.run();
        var end = Instant.now();
        Logger.info("================================================");
        Logger.info("Suggesting JVM to run the GC as soon as possible (Request might be ignored!)");
        System.gc();
        Logger.info("Completed {} in {}. - Invoking next task or going to sleep. It is safe to suspend execution if no other task is being invoked immediately.", id, Utility.humanReadableFormat(Duration.between(start, end)));
    }
}


