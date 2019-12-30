package updatetool.imdb.resolvement;

import org.tinylog.Logger;
import updatetool.api.AgentResolvementStrategy;
import updatetool.imdb.ImdbUtility;
import updatetool.imdb.ImdbDatabaseSupport.ImdbMetadataResult;

public class ImdbResolvement implements AgentResolvementStrategy<ImdbMetadataResult> {

    @Override
    public boolean resolve(ImdbMetadataResult toResolve) {
        toResolve.imdbId = ImdbUtility.extractId(ImdbUtility.IMDB, toResolve.guid);
        if(toResolve.imdbId == null) {
            Logger.error("Item: {} is detected as IMDB but has no id. (guid={})", toResolve.title, toResolve.guid);
            return false;
        }
        return true;
    }

}
