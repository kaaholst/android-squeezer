package uk.org.ngo.squeezer;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.CheckBox;
import android.widget.TextView;

import androidx.annotation.NonNull;

import java.util.Collection;

import uk.org.ngo.squeezer.model.LyrionPlayer;

class PlayerDropdownAdapter extends ArrayAdapter<LyrionPlayer> {
    private final LyrionPlayer activePlayer;
    private boolean continuePlayback;

    public PlayerDropdownAdapter(Context actionBarContext, Collection<LyrionPlayer> connectedPlayers, LyrionPlayer activePlayer) {
        super(actionBarContext, 0);
        add(null);
        addAll(connectedPlayers);
        this.activePlayer = activePlayer;
    }

    @Override
    public @NonNull View getView(int position, View convertView, @NonNull ViewGroup parent) {
        LyrionPlayer item = getItem(position);
        if (item == null) {
            View view = LayoutInflater.from(getContext()).inflate(R.layout.continue_playback, parent, false);
            view.setOnClickListener(v -> {
                continuePlayback = !continuePlayback;
                view.<CheckBox>findViewById(R.id.checkbox).setChecked(continuePlayback);
            });
            return view;
        } else {
            TextView view = (TextView) LayoutInflater.from(getContext()).inflate(R.layout.dropdown_item, parent, false);
            view.setText(item.getName());
            return view;
        }
    }

    @Override
    public boolean isEnabled(int position) {
        LyrionPlayer item = getItem(position);
        return !(item == null || item.equals(activePlayer));
    }

    public boolean continuePlayback() {
        return continuePlayback;
    }
}
