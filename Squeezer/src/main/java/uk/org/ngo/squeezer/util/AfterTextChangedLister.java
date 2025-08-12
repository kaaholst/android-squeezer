package uk.org.ngo.squeezer.util;

import android.text.Editable;
import android.text.TextWatcher;

abstract public class AfterTextChangedLister implements TextWatcher {
    @Override
    public void beforeTextChanged(CharSequence s, int start, int count, int after) {
    }

    @Override
    public void onTextChanged(CharSequence s, int start, int before, int count) {
    }

    @Override
    abstract public void afterTextChanged(Editable editable);
}
