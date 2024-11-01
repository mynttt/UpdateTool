package updatetool.imdb;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import updatetool.common.DatabaseSupport.Library;
import updatetool.common.DatabaseSupport.NewAgentSeriesType;
import updatetool.imdb.ImdbDatabaseSupport.ImdbMetadataResult;
import updatetool.imdb.ImdbPipeline.ImdbPipelineConfiguration;

public class ImdbLibraryMetadata {
    private final HashMap<String, List<ImdbMetadataResult>> metadata = new HashMap<>();
    private final HashMap<Integer, ImdbMetadataResult> mapping = new HashMap<>();
    
    private ImdbLibraryMetadata() {}

    public static ImdbLibraryMetadata fetchAll(List<Library> libraries, ImdbDatabaseSupport db, ImdbPipelineConfiguration configuration) {
        var meta = new ImdbLibraryMetadata();
        for(var lib : libraries) {
            List<ImdbMetadataResult> list = new ArrayList<>(100);

            var items = db.requestEntries(lib.id, lib.type);
            list.addAll(items.results);
            meta.mapping.putAll(items.mapping);

            var seriesRoot = db.requestTvSeriesRoot(lib.id);
            list.addAll(seriesRoot.results);
            meta.mapping.putAll(seriesRoot.mapping);

            var seasonRoot = db.requestTvSeasonRoot(lib.id);
            list.addAll(seasonRoot.results);
            meta.mapping.putAll(seasonRoot.mapping);

            meta.metadata.put(lib.uuid, list);
        }
        return meta;
    }

    public List<ImdbMetadataResult> request(String uuid) {
        return metadata.get(uuid);
    }

    public String getFullQualifiedName(ImdbMetadataResult result) {
        if((result.seriesType == NewAgentSeriesType.EPISODE || result.seriesType == NewAgentSeriesType.SEASON) && result.parentId != 0) {
            var parent = result.parentId;
            ImdbMetadataResult current = result;
            StringBuilder sb = new StringBuilder();

            while(true) {
                sb.append(current.title);
                sb.append(" <- ");

                var next = mapping.get(parent);

                if(next != null) {
                    parent = next.parentId;
                    current = next;
                } else {
                    break;
                }
            }

            sb.delete(sb.length()-4, sb.length());
            return sb.toString();
        }

        return result.title;
    }

}
