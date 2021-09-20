package updatetool.common;

import java.net.URLDecoder;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import javax.annotation.concurrent.NotThreadSafe;
import org.tinylog.Logger;
import updatetool.imdb.ImdbDockerImplementation;

@NotThreadSafe
public final class ExtraData {
    private Map<String, String> mapping = new LinkedHashMap<>();

    private ExtraData() {}
    
    public boolean contains(Pair<String, String> mapping) {
        String v = this.mapping.get(mapping.getKey());
        if(Objects.equals(v, mapping.getValue()))
            return true;
        return false;
    }
    
    public boolean containsAny(List<Pair<String, String>> mapping) {
        for(var kv : mapping) {
            if(contains(kv))
                return true;
        }
        return false;
    }
    
    public boolean containsKey(String key) {
        return mapping.containsKey(key);
    }
    
    public void deleteKey(String key) {
        mapping.remove(key);
    }
    
    public void prepend(String key, String value) {
        var map = new LinkedHashMap<String, String>();
        map.put(key, value);
        mapping.remove(key);
        map.putAll(mapping);
        this.mapping = map;
    }
    
    public String toURI() {
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
    
    private String handlePlexEncoding(String s) {
     // Plex has some interesting encoding behavior that does not match with the java default implementation
        return URLEncoder.encode(s, StandardCharsets.UTF_8)
                .replaceAll("\\.", "%2E")
                .replaceAll("\\%2D", "-")
                .replaceAll("\\_", "%5F")
                .replaceAll("\\*", "%2A")
                .replaceAll("\\%7E", "~");
    }
    
    public static ExtraData of(String uri) {
        ExtraData u = new ExtraData();
        
        if(uri != null) {
            String[] components = uri.split("\\&");
            for(String pair : components) {
                if(pair.isBlank())
                    continue;
                String[] kv = pair.split("\\=");
                
                String key, value;
                
                if(kv.length > 2) {
                    Logger.warn("ParsedURI: Encountered more than 2 K/V entries. Garbage date or bug? ({})", uri);
                    continue;
                } else if(kv.length == 1) {
                    key = kv[0].trim();
                    value = "";
                } else {
                    key = kv[0].trim();
                    value = kv[1].trim();
                }
                
                try { 
                    u.mapping.put(URLDecoder.decode(key, StandardCharsets.UTF_8), URLDecoder.decode(value, StandardCharsets.UTF_8));
                } catch(Exception e) {
                    Logger.error("Tried to decode [KEY] := '{}'", key);
                    Logger.error("Tried to decode [VALUE] := '{}'", value);
                    if(!ImdbDockerImplementation.checkCapability(Capabilities.DONT_THROW_ON_ENCODING_ERROR)) {
                        throw Utility.rethrow(e);
                    }
                }
            }
        }
        
        return u;
    }

    @Override
    public String toString() {
        return "ExtraData [mapping=" + mapping + "]";
    }
}