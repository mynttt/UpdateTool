package updatetool.imdb.resolvement;

import org.tinylog.Logger;
import updatetool.api.AgentResolvementStrategy;
import updatetool.imdb.ImdbDatabaseSupport.ImdbMetadataResult;

public class DefaultResolvement implements AgentResolvementStrategy<ImdbMetadataResult> {

    @Override
    public boolean resolve(ImdbMetadataResult toResolve) {
        Logger.warn("Item: {} has no matching IMDB resolver and will be ignored. (guid={})", toResolve.title, toResolve.guid);
        return false;
    }

}
