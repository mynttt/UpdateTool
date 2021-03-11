package updatetool.imdb.resolvement;

import updatetool.api.AgentResolvementStrategy;
import updatetool.common.externalapis.AbstractApi;
import updatetool.common.externalapis.TmdbApi;
import updatetool.common.externalapis.AbstractApi.ApiVersion;
import updatetool.imdb.ImdbDatabaseSupport.ImdbMetadataResult;

public class TmdbToImdbResolvement implements AgentResolvementStrategy<ImdbMetadataResult> {
    private final TmdbApi api;
   
    public TmdbToImdbResolvement(TmdbApi api) {
        this.api = api;
    }

    @Override
    public boolean resolve(ImdbMetadataResult toResolve) {
        ((AbstractApi) api).resolveImdbIdForItem(toResolve);
        return toResolve.resolved;
    }
    
    public ApiVersion getVersion() {
        return ((AbstractApi) api).version(); 
    }
}