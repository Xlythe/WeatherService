package android.support.v4.util;

import java.util.Map;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

/**
 * Due to Google Play Services still depending on the old support libraries, and the old libraries
 * not being compile-time compatible with the new libraries, we have 'ported' the necessary classes
 * so that everything runs the latest and greatest code.
 *
 * If a runtime crash occurs, we may have missed a class that we had to port.
 *
 * Note that if/when Google Play Services updates to the new libraries, this compat files simply
 * won't be called any longer.
 */
public class SimpleArrayMap<K, V> {
    private final androidx.collection.SimpleArrayMap<K, V> map;

    public SimpleArrayMap() {
        this.map = new androidx.collection.ArrayMap<>();
    }

    public SimpleArrayMap(int capacity) {
        this.map = new androidx.collection.ArrayMap<>(capacity);
    }

    public SimpleArrayMap(androidx.collection.SimpleArrayMap map) {
        this.map = new androidx.collection.SimpleArrayMap<>(map);
    }

    @Nullable
    public V get(@Nullable Object key) {
        return map.get(key);
    }

    @Nullable
    public V put(@NonNull K key, @NonNull V value) {
        return map.put(key, value);
    }

    public void putAll(Map<? extends K, ? extends V> map) {
        for (Map.Entry<? extends K, ? extends V> entry : map.entrySet()) {
            put(entry.getKey(), entry.getValue());
        }
    }

    @Nullable
    public V remove(@Nullable Object key) {
        return map.remove(key);
    }

    public void clear() {
        map.clear();
    }

    public boolean containsKey(@Nullable Object key) {
        return map.containsKey(key);
    }

    public boolean containsValue(@Nullable Object value) {
        return map.containsValue(value);
    }

    public int size() {
        return map.size();
    }

    public boolean isEmpty() {
        return map.isEmpty();
    }

    @Override
    public boolean equals(@Nullable Object obj) {
        return map.equals(obj);
    }

    @Override
    public int hashCode() {
        return map.hashCode();
    }
}
