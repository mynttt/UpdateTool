package updatetool.imdb.resolvement;

import java.util.Arrays;
import java.util.List;
import org.tinylog.Logger;
import updatetool.api.AgentResolvementStrategy;
import updatetool.common.DatabaseSupport.LibraryType;
import updatetool.common.KeyValueStore;
import updatetool.common.externalapis.AbstractApi.ApiVersion;
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
        String candidateTmp = cache.lookup(toResolve.guid);

        if(candidateTmp == null) {
            Logger.error("No external id associated with guid {} ({}).", toResolve.guid, toResolve.title);
            return false;
        }
        
        int isImdb = -1, isTmdb = -1, isTvdb = -1;
        List<String> candidates = Arrays.asList(candidateTmp.split("\\|"));
        for(int i = 0; i < candidates.size(); i++) {
            if(candidates.get(i).startsWith("imdb://")) { isImdb = i; };
            if(candidates.get(i).startsWith("tmdb://")) { isTmdb = i; };
            if(candidates.get(i).startsWith("tvdb://")) { isTvdb = i; };
        }
        
        // IMDB ID is present
        if(isImdb > -1) {
            toResolve.imdbId = ImdbUtility.extractId(ImdbUtility.IMDB, candidates.get(isImdb));
            return true;
        }
        
        if(isImdb + isTmdb + isTvdb == -3) {
            Logger.warn("Unhandled external ID in ({} => {}) = ({}). Please contact the author of the tool if you want to diagnose this further.", toResolve.type, toResolve.title, candidateTmp);
            return false;
        }
        
        boolean success = false;
        if(toResolve.type == LibraryType.MOVIE) {
            if(isTmdb > -1) {
                success = fallbackTmdb(toResolve, candidates.get(isTmdb));
                if(success) return true;
            }
            
            if(isTvdb > -1) {
                success = fallbackTvdb(toResolve, candidates.get(isTvdb));
                if(success) return true;
            }
        } else {
            if(isTvdb > -1) {
                success = fallbackTvdb(toResolve, candidates.get(isTvdb));
                if(success) return true;
            }
            
            if(isTmdb > -1) {
                success = fallbackTmdb(toResolve, candidates.get(isTmdb));
                if(success) return true;
            }
        }
        
        return success;
    }
    
    private boolean fallbackTmdb(ImdbMetadataResult toResolve, String candidate) {
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
    }
    
    private boolean fallbackTvdb(ImdbMetadataResult toResolve, String candidate) {
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
    }
}
