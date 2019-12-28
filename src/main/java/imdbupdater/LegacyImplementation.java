package imdbupdater;

import static common.Utility.seperator;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;
import java.util.Scanner;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.function.Consumer;
import java.util.stream.Collectors;
import org.tinylog.Logger;
import common.DatabaseSupport;
import common.DatabaseSupport.LibraryItem;
import common.SqliteDatabaseProvider;
import common.State;
import imdbupdater.Main.Implementations;
import imdbupdater.api.Implementation;
import imdbupdater.api.Job;
import imdbupdater.api.JobReport;
import imdbupdater.api.JobReport.StatusCode;
import imdbupdater.imdb.ImdbDatabaseSupport;
import imdbupdater.imdb.ImdbJob;
import imdbupdater.imdb.ImdbJobRunner;
import imdbupdater.imdb.ImdbOmdbCache;
import imdbupdater.imdb.ImdbPipeline;

public class LegacyImplementation implements Implementation {
    private final String ROOT_TO_DB = "Plug-in Support/Databases/com.plexapp.plugins.library.db";
    private final ArrayDeque<ImdbJob> QUEUE = new ArrayDeque<>();
    private final List<String> OPTIONS = List.of("Run jobs", "Create jobs", "Remove jobs", "Exit");
    private Path root;
    private SqliteDatabaseProvider provider;

    @Override
    public void invoke(String[] args) throws Exception {

        if(args.length != 3) {
            Main.printHelp(Implementations.of(args[0]), true);
            System.exit(-1);
        }

        String rootdir = args[1];
        String apikey = args[2];
        root = Paths.get(rootdir);

        if(!Files.exists(root) || !Files.isDirectory(root)) {
            System.err.println("Supplied plex data root does not exist @ " + root.toAbsolutePath().toString());
            System.exit(-1);
        }

        Main.testApiImdb(apikey);

        System.out.println("PWD: " + Main.PWD.toAbsolutePath().toString());
        System.out.println("Plex Data: " + root.toAbsolutePath().toString());
        System.out.println();

        Set<ImdbJob> state = State.recoverImdb(Main.STATE_IMDB);
        QUEUE.addAll(state);

        Main.PRINT_STATUS = true;

        if(!state.isEmpty())
            System.out.println("Loaded " + state.size() + " unfinished job(s).\n");

        var cache = ImdbOmdbCache.of(Main.CACHE_IMDB, 7);

        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            try {
                ImdbOmdbCache.dump(Main.CACHE_IMDB, cache);
            } catch (IOException e) {
                Logger.error("Failed to save cache.");
                Logger.error(e);
            }
        }));

        provider = new SqliteDatabaseProvider(root.resolve(ROOT_TO_DB).toAbsolutePath().toString());
        ImdbDatabaseSupport db = new ImdbDatabaseSupport(provider);
        ExecutorService service = Executors.newWorkStealingPool();

        while(true) {
            int choice = printMenu(OPTIONS, seperator("Select an action"), false, false);

            System.out.println(seperator(OPTIONS.get(choice)));
            System.out.println();
            switch(choice) {
            case 0:
                int s = runJobs(db, service, apikey, state, QUEUE, root, cache);
                if(s == 0 || s == -1) {
                    provider.close();
                    System.exit(s);
                }
                break;
            case 1:
                createJobs(db, state);
                State.dump(Main.STATE_IMDB, state);
                break;
            case 2:
                removeJobs(state);
                State.dump(Main.STATE_IMDB, state);
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

    public int runJobs(ImdbDatabaseSupport db, ExecutorService service, String apikey, Set<ImdbJob> state, ArrayDeque<ImdbJob> queue, Path root, ImdbOmdbCache cache) {
        if(state.isEmpty()) {
            System.out.println("Job queue is currently empty");
            return -2;
        }
        var runner = new ImdbJobRunner();
        var pipeline = new ImdbPipeline(db, service, apikey, cache, root.resolve("Metadata/Movies"));
        boolean error = false;
        JobReport lastReport = null;

        while(!queue.isEmpty()) {
            var current = queue.pop();
            System.out.println(seperator("Starting job: " + current.library + " @ " + current.uuid));
            System.out.println();
            lastReport = runner.run(current, pipeline);
            if(lastReport.code == StatusCode.PASS) {
                state.remove(current);
                System.out.println(seperator("Job " + current.library + " with UUID " + current.uuid + " finished successfully"));
                System.out.println();
            } else {
                error = true;
                break;
            }
        }

        try {
            State.dump(Main.STATE_IMDB, state);
        } catch (Exception e) {
            System.err.println(seperator("Failed to persist state."));
            e.printStackTrace();
        }

        if(!error) {
            System.out.println(seperator("All jobs finished correctly."));
            return 0;
        }

        if(lastReport.code == StatusCode.RATE_LIMIT) {
            System.out.println("");
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

    private void createJobs(ImdbDatabaseSupport db, Set<ImdbJob> state) {
        var libraries = new DatabaseSupport(provider).requestLibraries();
        if(libraries.isEmpty()) {
            System.out.println("No movie libraries with an IMDB agent found in your database.");
            return;
        }
        libraries = libraries.stream().filter(l -> !state.contains(new ImdbJob(null, l.uuid))).collect(Collectors.toList());
        if(libraries.isEmpty()) {
            System.out.println("All libraries are currently queued as jobs.");
            return;
        }
        libraries.sort((i1, i2) -> i1.name.compareToIgnoreCase(i2.name));
        int choice = printMenu(libraries, "Select a movie library to run this on (libraries without IMDB agent or already created as job excluded)!", true, true);

        Consumer<LibraryItem> f = i -> {
            var job = new ImdbJob(i.name, i.uuid);
            QUEUE.add(job);
            state.add(job);
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

    private void removeJobs(Set<ImdbJob> state) {
        if(state.isEmpty()) {
            System.out.println("Job queue is currently empty");
            return;
        }

        var jobs = new ArrayList<>(state);
        jobs.sort((j1, j2) -> Integer.compare(j1.stage.ordinal(), j2.stage.ordinal()));
        int choice = printMenu(jobs, "Select a job to remove (no reverse of modified DB/XML)", true, true);

        Consumer<Job> f = j -> {
            state.remove(j);
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

    private void exit(Set<ImdbJob> state) throws Exception {
        State.dump(Main.STATE_IMDB, state);
        System.out.println("Goodbye!");
        System.exit(0);
    }

    private int printMenu(List<? extends Object> list, String message, boolean catchAll, boolean canCancel) {
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
}
