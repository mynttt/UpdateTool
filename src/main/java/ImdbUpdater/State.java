package ImdbUpdater;

import java.io.IOException;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonSyntaxException;
import ImdbUpdater.OMDBApi.OMDBResponse;
import ImdbUpdater.PlexDatabaseSupport.MetadataResult;

public class State {
    public static final Charset UTF_8 = StandardCharsets.UTF_8;
    public static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    public final Set<Job> jobs = new HashSet<>();

    public enum Stage {
        CREATED, ANALYSED_DB, ACCUMULATED_META, TRANSFORMED_META, DB_UPDATED, COMPLETED
    }

    public static class Job {
        public boolean dbmode;
        public String library;
        public String uuid;
        public Stage stage = Stage.CREATED;
        public Instant lastActivity;
        public List<MetadataResult> items = new ArrayList<>();
        public Map<String, OMDBResponse> responses = new HashMap<>();

        public Job(String library, String uuid, boolean dbmode) {
            this.library = library;
            this.uuid = uuid;
            this.dbmode = dbmode;
            touch();
        }

        public void touch() {
            this.lastActivity = Instant.now();
        }

        private Job(String uuid) {
            this.uuid = uuid;
        }

        @Override
        public String toString() {
            return String.format("%-20s | %-15s | DBMode: %b | Last activity: %s", library, stage, dbmode, lastActivity);
        }

        @Override
        public int hashCode() {
            return Objects.hash(uuid);
        }

        @Override
        public boolean equals(Object obj) {
            if (this == obj)
                return true;
            if (obj == null)
                return false;
            if (getClass() != obj.getClass())
                return false;
            Job other = (Job) obj;
            return Objects.equals(uuid, other.uuid);
        }
    }

    public boolean uniqueUUID(String uuid) {
        return !jobs.contains(new Job(uuid));
    }

    public static State recover(Path p) throws JsonSyntaxException, IOException {
        return GSON.fromJson(Files.readString(p, UTF_8), State.class);
    }

    public static void dump(Path p, State o) throws IOException {
        Files.writeString(p, GSON.toJson(o), UTF_8);
    }

}
