package updatetool.common;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

public abstract class ExtraData {
    protected Map<String, String> mapping = new LinkedHashMap<>();
    
    public static ExtraData of(String data)  {
        
        // New Extra Data is never null
        if(data == null)
            return new OldExtraData(null);
        
        if(data.startsWith("{")) {
            return new NewExtraData(data);
        } else {
            return new OldExtraData(data);
        }
    }
    
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
    
    public void updateBadge(String key, String value) {
        var map = new LinkedHashMap<String, String>();
        map.put(key, value);
        mapping.remove(key);
        map.putAll(mapping);
        this.mapping = map;
    }
    
    public abstract String export();
}