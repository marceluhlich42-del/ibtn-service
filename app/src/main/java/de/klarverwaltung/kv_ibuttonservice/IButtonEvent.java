package de.klarverwaltung.kv_ibuttonservice;

import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;

public class IButtonEvent {
    private static final IButtonEvent instance = new IButtonEvent();
    private final MutableLiveData<IbuttonResult> liveData = new MutableLiveData<>();

    private IButtonEvent() {}

    public static IButtonEvent getInstance() {
        return instance;
    }

    public LiveData<IbuttonResult> getLiveData() {
        return liveData;
    }

    public void postEvent(IbuttonResult event) {
        liveData.postValue(event);
    }
}
