package updatetool.common.externalapis;

import java.util.Map;
import org.tinylog.Logger;
import com.google.gson.Gson;
import com.jayway.jsonpath.Configuration;
import com.jayway.jsonpath.JsonPath;
import com.jayway.jsonpath.Option;
import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import updatetool.common.KeyValueStore;
import updatetool.common.Utility;
import updatetool.imdb.ImdbDatabaseSupport.ImdbMetadataResult;

//TODO: new v4 support and v3 legacy lookup!

@SuppressWarnings("unused")
public class TvdbApiV4 extends AbstractApi implements TmdbApi {
    private static final Configuration CONFIG = Configuration.defaultConfiguration().setOptions(Option.SUPPRESS_EXCEPTIONS);
    private static final String APPLICATION_KEY = "17e55156-4eff-4a8b-950f-96f805e15878";
    private static final String BASE_URL = "https://api4.thetvdb.com/v4/";
    private final String authToken;
    private final KeyValueStore cache, blacklist;
    
    public TvdbApiV4(String pin, KeyValueStore cache, KeyValueStore blacklist) {
        authToken = auth(pin);
        this.cache = cache;
        this.blacklist = blacklist;
    }
    
    @SuppressFBWarnings("DM_EXIT")
    private String auth(String pin) {
        Logger.info("Testing TMDB API (v4) pin: " + pin);
        try {
            var response = send(postJson(BASE_URL + "login", new Gson().toJson(Map.of("apikey", APPLICATION_KEY, "pin", pin))));
            var status = JsonPath.using(CONFIG).parse(response.body()).read("$.status");
            if(response.statusCode() != 200 || !"success".equals(status)) {
                Logger.error("TVDB v4 authorization failed with code {} and status {}", response.statusCode(), status);
                Logger.error("This could be due to the TVDB API v4 having issues at the moment or your credentials being wrong.");
                Logger.error("This is the received response:");
                Logger.error("Payload:" + response.body());
                Logger.error("Pin (v4) available under: https://www.thetvdb.com");
                Logger.error("===================================================");
                System.exit(-1);
            }
            Logger.info("Test passed. API Key is valid.");
            return JsonPath.read(response.body(), "$.data.token");
        } catch (Exception e) {
            throw Utility.rethrow(e);
        }
    }

    @Override
    public void resolveImdbIdForItem(ImdbMetadataResult result) {
        // TODO Auto-generated method stub
    }

    @Override
    public ApiVersion version() {
        return ApiVersion.TVDB_V4;
    }
    
}
