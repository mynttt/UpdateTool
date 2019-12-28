package common;

import java.io.IOException;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.Set;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;
import imdbupdater.api.Job;
import imdbupdater.imdb.ImdbJob;

public class State <T extends Job> {
    public static final Charset UTF_8 = StandardCharsets.UTF_8;
    public static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    private State() {}

    public static Set<ImdbJob> recoverImdb(Path p)  {
        try {
            return GSON.fromJson(Files.readString(p, UTF_8), new TypeToken<Set<ImdbJob>>() {}.getType());
        } catch (Exception e) {
            return new HashSet<>();
        }
    }

    public static <T extends Job> void dump(Path p, Set<T> o) throws IOException {
        Files.writeString(p, GSON.toJson(o), UTF_8);
    }

}