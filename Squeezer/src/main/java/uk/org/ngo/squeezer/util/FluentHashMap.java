package uk.org.ngo.squeezer.util;

import java.util.HashMap;

public class FluentHashMap<K, V> extends HashMap<K, V> {

    public FluentHashMap<K, V> with(K key, V value) {
        put(key, value);
        return this;
    }
}
