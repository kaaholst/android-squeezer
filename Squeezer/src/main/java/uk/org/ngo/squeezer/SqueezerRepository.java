package uk.org.ngo.squeezer;

import android.os.Looper;

import androidx.lifecycle.LifecycleOwner;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.Observer;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class SqueezerRepository {
    private final Map<Class<?>, MutableLiveData<?>> liveData = new ConcurrentHashMap<>();

    @SuppressWarnings("unchecked")
    private <T> MutableLiveData<T> get(Class<T> clazz) {
        MutableLiveData<T> data = (MutableLiveData<T>) liveData.get(clazz);
        if (data == null) {
            liveData.put(clazz, data = new MutableLiveData<>());
        }
        return data;
    }

    @SuppressWarnings("unchecked")
    public <T> void observe(LifecycleOwner owner, Observer<T> observer, T[] ... reified) {
        MutableLiveData<T> data = get((Class<T>) reified.getClass().getComponentType());
        data.observe(owner, t -> {
            if (t != null) {
                observer.onChanged(t);
            }
        });
    }

    @SuppressWarnings("unchecked")
    public <T> void post(T event, T[] ... reified) {
        MutableLiveData<T> data = get((Class<T>) reified.getClass().getComponentType());
        if (Looper.getMainLooper() == Looper.myLooper()) data.setValue(event); else data.postValue(event);
    }

    @SuppressWarnings("unchecked")
    public <T> void observeForever(Observer<T> observer, T[] ... reified) {
        MutableLiveData<T> data = get((Class<T>) reified.getClass().getComponentType());
        data.observeForever(t -> {
            if (t != null) {
                observer.onChanged(t);
            }
        });
    }

    @SuppressWarnings("unchecked")
    public <T> void removeObserver(Observer<T> observer, T[] ... reified) {
        // TODO this is not the observer, see observeForever
        MutableLiveData<T> data = get((Class<T>) reified.getClass().getComponentType());
        data.removeObserver(observer);
    }

    public void removeEvents() {
        liveData.values().forEach(data -> data.setValue(null));
    }
}
