package updatetool.imdb;

import java.util.HashMap;
import java.util.List;
import updatetool.common.DatabaseSupport.Library;
import updatetool.common.DatabaseSupport.LibraryType;
import updatetool.imdb.ImdbDatabaseSupport.ImdbMetadataResult;
import updatetool.imdb.ImdbPipeline.ImdbPipelineConfiguration;

public class ImdbLibraryMetadata {
    private final HashMap<String, List<ImdbMetadataResult>> metadata = new HashMap<>();
    
    private ImdbLibraryMetadata() {}

    public static ImdbLibraryMetadata fetchAll(List<Library> libraries, ImdbDatabaseSupport db, ImdbPipelineConfiguration configuration) {
        var meta = new ImdbLibraryMetadata();
        for(var lib : libraries) {
            var items = db.requestEntries(lib.id, lib.type);
            if(configuration.resolveTvdb() && lib.type == LibraryType.SERIES) {
                items.addAll(db.requestTvSeriesRoot(lib.id));
                items.addAll(db.requestTvSeasonRoot(lib.id));
            }
            meta.metadata.put(lib.uuid, items);
        }
        return meta;
    }

    public List<ImdbMetadataResult> request(String uuid) {
        return metadata.get(uuid);
    }

}
