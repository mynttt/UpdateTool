package updatetool.imdb.resolvement;

import org.tinylog.Logger;
import updatetool.api.AgentResolvementStrategy;
import updatetool.common.KeyValueStore;
import updatetool.common.externalapis.AbstractApi.ApiVersion;
import updatetool.common.DatabaseSupport.LibraryType;
import updatetool.imdb.ImdbDatabaseSupport.ImdbMetadataResult;
import updatetool.imdb.ImdbUtility;

public class NewPlexAgentToImdbResolvement implements AgentResolvementStrategy<ImdbMetadataResult> {
    private final KeyValueStore cache;
    private final TmdbToImdbResolvement fallbackTmdb;
    private final TvdbToImdbResolvement fallbackTvdb;
    
    public NewPlexAgentToImdbResolvement(KeyValueStore cache, TmdbToImdbResolvement fallbackTmdb, TvdbToImdbResolvement fallbackTvdb) {
        this.cache = cache;
        this.fallbackTmdb = fallbackTmdb;
        this.fallbackTvdb = fallbackTvdb;
        
        if(fallbackTmdb == null)
            Logger.warn("No TMDB fallback set. Will not resolve new plex agent items if they only have a TMDB id associated.");
        
        if(fallbackTvdb == null)
            Logger.warn("No TVDB fallback set. Will not resolve new plex agent items if they only have a TVDB id associated.");
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
        } else if(candidate.startsWith("tmdb")) {
            if(fallbackTmdb == null) {
                return false;
            }
            
            //TODO: TMDB v3 API is incapable of resolving this at the moment
            if(toResolve.type == LibraryType.SERIES && fallbackTmdb.getVersion() == ApiVersion.TMDB_V3)
                return false;
            
            String oldGuid = toResolve.guid;
            toResolve.guid = candidate;
            boolean success = fallbackTmdb.resolve(toResolve);
            toResolve.guid = oldGuid;
            return success;
        } else if(candidate.startsWith("tvdb")) {
            if(fallbackTvdb == null) {
                return false;
            }
            
            //TODO: TVDB v3 API is incapable of resolving this at the moment
            if(toResolve.type == LibraryType.SERIES && fallbackTvdb.getVersion() == ApiVersion.TVDB_V3)
                return false;
            
            String oldGuid = toResolve.guid;
            toResolve.guid = candidate;
            boolean success = fallbackTvdb.resolve(toResolve);
            toResolve.guid = oldGuid;
            return success;
        } else {
            Logger.warn("Unhandled external {} id ({}). Please contact the author of the tool.", toResolve.type, candidate);
            return false;
        }
    }
}
