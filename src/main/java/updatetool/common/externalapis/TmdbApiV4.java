package updatetool.common.externalapis;

import updatetool.imdb.ImdbDatabaseSupport.ImdbMetadataResult;

//TODO: new v4 support and v3 legacy lookup
// TMDB API v4 appears to only be used to manage the user account and user account items like personal watchlists (07.02.2022)

public class TmdbApiV4 extends AbstractApi implements TmdbApi {
    
    @Override
    public void resolveImdbIdForItem(ImdbMetadataResult result) {
        // TODO Auto-generated method stub
        
    }

    @Override
    public ApiVersion version() {
        return ApiVersion.TMDB_V3;
    }
    
    
}
