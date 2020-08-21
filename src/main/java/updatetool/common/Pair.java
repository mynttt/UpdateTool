package updatetool.common;

import java.util.Objects;

public final class Pair<K, V> {
    private final K key;
    private final V value;
    
    private Pair(K key, V value) {
        this.key = key;
        this.value = value;
    }
    
    public K getKey() {
        return key;
    }
    
    public V getValue() {
        return value;
    }
    
    public static <K, V> Pair<K, V> of(K key, V value) {
        Objects.requireNonNull(key, "key must not be null");
        return new Pair<>(key, value);
    }

    @Override
    public int hashCode() {
        return Objects.hash(key, value);
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj)
            return true;
        if (obj == null)
            return false;
        if (getClass() != obj.getClass())
            return false;
        @SuppressWarnings("rawtypes")
        Pair other = (Pair) obj;
        return Objects.equals(key, other.key) && Objects.equals(value, other.value);
    }
}
