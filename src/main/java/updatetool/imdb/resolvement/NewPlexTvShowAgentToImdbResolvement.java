package updatetool.imdb.resolvement;

import org.tinylog.Logger;
import updatetool.api.AgentResolvementStrategy;
import updatetool.common.KeyValueStore;
import updatetool.imdb.ImdbUtility;
import updatetool.imdb.ImdbDatabaseSupport.ImdbMetadataResult;

public class NewPlexTvShowAgentToImdbResolvement implements AgentResolvementStrategy<ImdbMetadataResult>  {
    private final KeyValueStore cache;
    
    public NewPlexTvShowAgentToImdbResolvement(KeyValueStore cache) {
        this.cache = cache;
    }

    @Override
    public boolean resolve(ImdbMetadataResult toResolve) {
        String candidate = cache.lookup(toResolve.guid);

        if(candidate == null) {
            Logger.error("No external id associated with guid {} ({}).", toResolve.guid, toResolve.title);
            return false;
        }
        
        if(candidate.startsWith("imdb")) {
            toResolve.imdbId = ImdbUtility.extractId(ImdbUtility.IMDB, candidate);
            return true;
        } else {
            // TODO: resolvement for other agents requires proper TVDB v4 API implementation
            return false;
        }
    }

}
