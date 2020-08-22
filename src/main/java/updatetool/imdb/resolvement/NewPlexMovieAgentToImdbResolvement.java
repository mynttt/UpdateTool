package updatetool.imdb.resolvement;

import org.tinylog.Logger;
import updatetool.api.AgentResolvementStrategy;
import updatetool.common.KeyValueStore;
import updatetool.imdb.ImdbUtility;
import updatetool.imdb.ImdbDatabaseSupport.ImdbMetadataResult;

public class NewPlexMovieAgentToImdbResolvement implements AgentResolvementStrategy<ImdbMetadataResult> {
    private final KeyValueStore cache;
    private final TmdbMovieToImdbResolvement fallback;
    
    public NewPlexMovieAgentToImdbResolvement(KeyValueStore cache, TmdbMovieToImdbResolvement fallback) {
        this.cache = cache;
        this.fallback = fallback;
        
        if(fallback == null)
            Logger.warn("No TMDB fallback set. Will not resolve new plex movie agent items if they only have a TMDB id associated.");
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
            if(fallback == null) {
                Logger.warn("TMDB id associated with new plex movie agent guid but no TMDB resolvement enabled. Ignoring item: ({}, {}, {})", candidate, toResolve.guid, toResolve.title);
                return false;
            }
            
            String oldGuid = toResolve.guid;
            toResolve.guid = candidate;
            boolean success = fallback.resolve(toResolve);
            toResolve.guid = oldGuid;
            return success;
        } else {
            Logger.warn("Unhandled external id ({}). Please contact the author of the tool.", candidate);
            return false;
        }
    }
}
