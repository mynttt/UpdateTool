package updatetool.common;

import java.net.URLDecoder;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import javax.annotation.concurrent.NotThreadSafe;
import org.tinylog.Logger;
import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import updatetool.imdb.ImdbDockerImplementation;

@NotThreadSafe
public final class OldExtraData extends ExtraData  {

    @SuppressFBWarnings("DM_EXIT")
    protected OldExtraData(String uri) {
        
        if(uri != null) {
            String[] components = uri.split("\\&");
            for(String pair : components) {
                if(pair.isBlank())
                    continue;
                String[] kv = pair.split("\\=");
                
                String key, value;
                
                if(kv.length > 2) {
                    Logger.warn("ParsedURI: Encountered more than 2 K/V entries. Garbage date or bug? ({})", uri);
                    System.exit(-1);
                    continue;
                } else if(kv.length == 1) {
                    key = kv[0].trim();
                    value = "";
                } else {
                    key = kv[0].trim();
                    value = kv[1].trim();
                }
                
                try { 
                    mapping.put(URLDecoder.decode(key, StandardCharsets.UTF_8), URLDecoder.decode(value, StandardCharsets.UTF_8));
                } catch(Exception e) {
                    Logger.error("Tried to decode [KEY] := '{}'", key);
                    Logger.error("Tried to decode [VALUE] := '{}'", value);
                    if(!ImdbDockerImplementation.checkCapability(Capabilities.DONT_THROW_ON_ENCODING_ERROR)) {
                        throw Utility.rethrow(e);
                    }
                }
            }
        }
    }
    
    public void updateBadge(String key, String value) {
        var map = new LinkedHashMap<String, String>();
        map.put(key, value);
        mapping.remove(key);
        map.putAll(mapping);
        this.mapping = map;
    }
    
    private String handlePlexEncoding(String s) {
     // Plex has some interesting encoding behavior that does not match with the java default implementation
        return URLEncoder.encode(s, StandardCharsets.UTF_8)
                .replaceAll("\\.", "%2E")
                .replaceAll("\\%2D", "-")
                .replaceAll("\\_", "%5F")
                .replaceAll("\\*", "%2A")
                .replaceAll("\\%7E", "~");
    }

    @Override
    public String export() {
        if(mapping.isEmpty())
            return "";
        
        StringBuilder sb = new StringBuilder();
        for(var entry : mapping.entrySet()) {
            sb.append(handlePlexEncoding(entry.getKey()));
            sb.append("=");
            sb.append(handlePlexEncoding(entry.getValue()));
            sb.append("&");
        }
        sb.deleteCharAt(sb.length()-1);
        
        return sb.toString();
    }
}