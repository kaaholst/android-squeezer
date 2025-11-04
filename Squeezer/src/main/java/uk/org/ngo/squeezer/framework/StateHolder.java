package uk.org.ngo.squeezer.framework;

import androidx.lifecycle.ViewModel;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

public class StateHolder extends ViewModel {
    private final Map<String, Object> state = Collections.synchronizedMap(new HashMap<>());

    public <T> void put(String key, T value) {
        state.put(key, value);
    }

    @SuppressWarnings("unchecked")
    public <T> T get(String key) {
        return (T) state.get(key);
    }

}
