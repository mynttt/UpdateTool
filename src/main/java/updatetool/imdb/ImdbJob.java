package updatetool.imdb;

import java.util.ArrayList;
import java.util.List;
import updatetool.api.Job;
import updatetool.common.DatabaseSupport.Library;
import updatetool.imdb.ImdbDatabaseSupport.ImdbMetadataResult;

public class ImdbJob extends Job {
    public List<ImdbMetadataResult> items = new ArrayList<>();

    public ImdbJob(Library library) {
        super(library);
    }

    @Override
    public String whatKindOfJob() {
        return "IMDB";
    }

}