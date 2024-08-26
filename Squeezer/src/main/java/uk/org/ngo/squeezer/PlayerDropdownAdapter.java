package uk.org.ngo.squeezer;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.CheckBox;
import android.widget.TextView;

import androidx.annotation.NonNull;

import java.util.List;

import uk.org.ngo.squeezer.model.Player;

class PlayerDropdownAdapter extends ArrayAdapter<Player> {
    private final Player activePlayer;
    private boolean continuePlayback;

    public PlayerDropdownAdapter(Context actionBarContext, List<Player> connectedPlayers, Player activePlayer) {
        super(actionBarContext, 0);
        add(null);
        addAll(connectedPlayers);
        this.activePlayer = activePlayer;
    }

    @Override
    public @NonNull View getView(int position, View convertView, @NonNull ViewGroup parent) {
        Player item = getItem(position);
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
        Player item = getItem(position);
        return !(item == null || item.equals(activePlayer));
    }

    public boolean continuePlayback() {
        return continuePlayback;
    }
}
