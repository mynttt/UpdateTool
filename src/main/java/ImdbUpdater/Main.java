package ImdbUpdater;

import static ImdbUpdater.Utility.seperator;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;
import java.util.Scanner;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.function.Consumer;
import java.util.stream.Collectors;
import ImdbUpdater.JobRunner.JobReport;
import ImdbUpdater.JobRunner.JobReport.StatusCode;
import ImdbUpdater.PlexDatabaseSupport.LibraryItem;
import ImdbUpdater.State.Job;

public class Main {
    private static final String ROOT_TO_DB = "Library/Application Support/Plex Media Server/Plug-in Support/Databases/com.plexapp.plugins.library.db";
    private static final ArrayDeque<Job> QUEUE = new ArrayDeque<>();
    private static final List<String> OPTIONS = List.of("Run jobs", "Create jobs", "Remove jobs", "Exit");
    public static final Path PWD = Paths.get(".");
    public static final Path STATE = PWD.resolve("state.json");

    private static boolean dbmode;

    public static void main(String[] args) throws Exception {
        Thread.setDefaultUncaughtExceptionHandler((t, e) -> {
            System.err.println("Uncaught " + e.getClass().getSimpleName() + " exception encountered...");
            System.err.println("Please contact the maintainer of the application with the stacktrace below if you think this is unwanted behavior.");
            System.err.println("========================================");
            e.printStackTrace();
            System.err.println("========================================");
            System.err.println("The application will terminate now.");
            System.exit(-1);
        });

        Class.forName("org.sqlite.JDBC");

        if(args.length < 2 || args.length > 3) {
            System.out.println("java -jar ImdbUpdater dbmode <*.db> <api key>");
            System.out.println("Database Only Mode:");
            System.out.println(" -> Only updates the database and ignores the XML files.");
            System.out.println(" -> Useful if your Plex is on a server and can't easily install the JRE there.\n");
            System.out.println("java -jar ImdbUpdater <plexroot> <api key>");
            System.out.println("Normal Mode: ");
            System.out.println(" -> Does the same as the database only mode but also updates the XML fallback files.");
            System.out.println(" -> Requires a fully valid PlexMediaServer root folder structure.\n");
            System.out.println("Parameters:");
            System.err.println("<plexroot> : Path to root directory of plex data usually named PlexMediaServer");
            System.out.println("<*.db>     : Path to the database that plex uses usually named com.plexapp.plugins.library.db");
            System.out.println("<api key>  : OMDB API Key");
            System.exit(-1);
        }

        dbmode = args[0].toLowerCase().equals("dbmode");

        String rootdir = args[dbmode ? 1 : 0];
        String apikey = args[dbmode ? 2 : 1];
        Path root = Paths.get(rootdir);

        if(!dbmode) {
            if(!Files.exists(root)) {
                System.err.println("Supplied plex root does not exist @ " + root.toAbsolutePath().toString());
                System.exit(-1);
            }
            if(!Files.isDirectory(root) || !root.getFileName().toString().equals("PlexMediaServer")) {
                System.err.println("Invalid argument. Root dir must be a directory with the name of PlexMediaServer");
                System.err.println("Your input: " + root.toAbsolutePath().toString());
                System.exit(-1);
            }
        }

        System.out.println("Running in: " + (dbmode ? "DB Mode\n" : "Normal Mode\n"));

        State state;
        try {
            state = State.recover(STATE);
            QUEUE.addAll(state.jobs);
        } catch(Exception e) {
            state = new State();
        }

        System.out.println("PWD: " + PWD.toAbsolutePath().toString());
        System.out.println("Plex: " + root.toAbsolutePath().toString());
        System.out.println();

        testApi(apikey);
        System.out.println();

        PlexDatabaseSupport db = new PlexDatabaseSupport(dbmode ? Path.of(args[1]).toAbsolutePath().toString() : root.resolve(ROOT_TO_DB).toAbsolutePath().toString());
        ExecutorService service = Executors.newWorkStealingPool();

        if(!state.jobs.isEmpty())
            System.out.println("Loaded " + state.jobs.size() + " unfinished job(s).\n");

        while(true) {
            int choice = printMenu(OPTIONS, seperator("Select an action"), false, false);

            System.out.println(seperator(OPTIONS.get(choice)));
            System.out.println();
            switch(choice) {
            case 0:
                int s = runJobs(db, service, apikey, state, QUEUE, root);
                if(s == 0 || s == -1) {
                    db.close();
                    System.exit(s);
                }
                break;
            case 1:
                createJobs(db, state);
                State.dump(STATE, state);
                break;
            case 2:
                removeJobs(state);
                State.dump(STATE, state);
                break;
            case 3:
                exit(state);
                break;
            default:
                throw new RuntimeException("invalid selection in main switch");
            }
            System.out.println();
        }
    }

    public static int runJobs(PlexDatabaseSupport db, ExecutorService service, String apikey, State state, ArrayDeque<Job> queue, Path root) {
        if(state.jobs.isEmpty()) {
            System.out.println("Job queue is currently empty");
            return -2;
        }
        var runner = new JobRunner(db, service, apikey, root.resolve("Library/Application Support/Plex Media Server/Metadata/Movies/"));
        boolean error = false;
        JobReport lastReport = null;

        while(!queue.isEmpty()) {
            var current = queue.pop();
            System.out.println(seperator("Starting job: " + current.library + " @ " + current.uuid));
            System.out.println();
            lastReport = runner.run(current);
            if(lastReport.code == StatusCode.PASS) {
                state.jobs.remove(lastReport.progress);
                System.out.println(seperator("Job " + lastReport.progress.library + " with UUID " + lastReport.progress.uuid + " finished successfully"));
                System.out.println();
            } else {
                error = true;
                state.jobs.remove(current);
                state.jobs.add(lastReport.progress);
                break;
            }
        }

        try {
            State.dump(PWD.resolve(STATE), state);
        } catch (Exception e) {
            System.err.println(seperator("Failed to persist state."));
            e.printStackTrace();
        }

        if(!error) {
            System.out.println(seperator("All jobs finished correctly."));
            return 0;
        }

        if(lastReport.code == StatusCode.RATE_LIMIT) {
            System.out.println("Aborted job queue due to being rate limited. Either change the API key or wait a while to continue.");
            System.out.println("The progress has been saved.\n");
            System.out.println(seperator("Rate limit"));
        }

        if(lastReport.code == StatusCode.ERROR) {
            System.out.println("Aborted job queue due to an unexpected runtime error. Please send this to the maintainer of the application if you wish to file a bug report.");
            System.out.println("=======================================");
            lastReport.exception.printStackTrace();
            try { Thread.sleep(20); } catch(Exception e) {}
            System.out.println("=======================================");
            System.out.println("\nThe progress has been saved.\n");
            System.out.println(seperator(lastReport.exception.getClass().toString()));
        }
        return -1;
    }

    private static void createJobs(PlexDatabaseSupport db, State state) {
        var libraries = db.requestLibraries();
        if(libraries.isEmpty()) {
            System.out.println("No movie libraries with an IMDB agent found in your database.");
            return;
        }
        libraries = libraries.stream().filter(l -> state.uniqueUUID(l.uuid)).collect(Collectors.toList());
        if(libraries.isEmpty()) {
            System.out.println("All libraries are currently queued as jobs.");
            return;
        }
        libraries.sort((i1, i2) -> i1.name.compareToIgnoreCase(i2.name));
        int choice = printMenu(libraries, "Select a movie library to run this on (libraries without IMDB agent or already created as job excluded)!", true, true);

        Consumer<LibraryItem> f = i -> {
            Job job = new Job(i.name, i.uuid, dbmode);
            QUEUE.add(job);
            state.jobs.add(job);
        };

        if(choice == -2) {
            System.out.println("Canceled");
            return;
        }

        if(choice == -1) {
            libraries.forEach(f);
            System.out.println("Added all jobs!");
        } else {
            f.accept(libraries.get(choice));
            System.out.println("Added " + libraries.get(choice).name);
        }
    }

    private static void removeJobs(State state) {
        if(state.jobs.isEmpty()) {
            System.out.println("Job queue is currently empty");
            return;
        }

        var jobs = new ArrayList<>(state.jobs);
        jobs.sort((j1, j2) -> Integer.compare(j1.stage.ordinal(), j2.stage.ordinal()));
        int choice = printMenu(jobs, "Select a job to remove (no reverse of modified DB/XML)", true, true);

        Consumer<Job> f = j -> {
            state.jobs.remove(j);
            QUEUE.remove(j);
        };

        if(choice == -2) {
            System.out.println("Canceled");
            return;
        }

        if(choice == -1) {
            jobs.forEach(f);
            System.out.println("Removed all jobs!");
        } else {
            var j = jobs.get(choice);
            f.accept(j);
            System.out.println("Removed job for library " + j.library + ".");
        }
    }

    private static void exit(State state) throws Exception {
        State.dump(PWD.resolve(STATE), state);
        System.out.println("Goodbye!");
        System.exit(0);
    }

    private static int printMenu(List<? extends Object> list, String message, boolean catchAll, boolean canCancel) {
        int index = catchAll ? 1 : 0;
        int size = (catchAll ^ canCancel) ? list.size() : (catchAll && canCancel) ? list.size() + 1 : list.size() - 1;
        System.out.println(message);
        System.out.println();
        if(catchAll)
            System.out.println("0.) Select all options");
        for(int i = 0; i < list.size(); i++)
            System.out.println((i+index) +".) " + list.get(i).toString());
        if(canCancel)
            System.out.println(size + ".) Cancel selection");
        System.out.println();
        @SuppressWarnings("resource")
        Scanner input = new Scanner(System.in);
        while(true) {
            System.out.print("Make a choice [0-" + size +  "]: ");
            if(!input.hasNextInt()) {
                input.nextLine();
                continue;
            }
            int i = input.nextInt();
            if(i >= 0 && i <= size) {
                System.out.println();
                if(catchAll && i == 0)
                    return -1;
                if(canCancel && i == size)
                    return -2;
                return catchAll ? i-1 : i;
            }
        }
    }

    private static void testApi(String key) throws IOException, InterruptedException {
        System.out.println("Testing API key: " + key);
        var api = new OMDBApi(key);
        var response = api.testApi();
        if(response.statusCode() != 200) {
            System.err.println("API Test failed: Code " + response.statusCode());
            System.err.println("Payload:" + response.body());
            System.err.println("Key available under https://www.omdbapi.com/");
            System.exit(-1);
        }
        System.out.println("Test passed. API Key is valid.");
    }
}