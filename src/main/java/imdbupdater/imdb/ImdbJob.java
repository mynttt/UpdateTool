package imdbupdater.imdb;

import java.util.ArrayList;
import java.util.List;
import imdbupdater.api.Job;
import imdbupdater.imdb.ImdbDatabaseSupport.ImdbMetadataResult;

public class ImdbJob extends Job {
    public List<ImdbMetadataResult> items = new ArrayList<>();

    public ImdbJob(String library, String uuid) {
        super(library, uuid);
    }

    @Override
    public String whatKindOfJob() {
        return "IMDB";
    }

}