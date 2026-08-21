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
    public static final Player POWER_OFF_ALL = new Player(java.util.Map.of("playerid", "POWER_OFF_ALL", "name", "POWER_OFF_ALL"));
    private final Player activePlayer;
    private boolean continuePlayback;

    public PlayerDropdownAdapter(Context actionBarContext, List<Player> connectedPlayers, Player activePlayer) {
        super(actionBarContext, 0);
        add(null);
        addAll(connectedPlayers);
        add(POWER_OFF_ALL);
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
        } else if (item == POWER_OFF_ALL) {
            return LayoutInflater.from(getContext()).inflate(R.layout.dropdown_power_off_all, parent, false);
        } else {
            TextView view = (TextView) LayoutInflater.from(getContext()).inflate(R.layout.dropdown_item, parent, false);
            view.setText(item.getName());
            return view;
        }
    }

    @Override
    public boolean isEnabled(int position) {
        Player item = getItem(position);
        if (item == null) {
            return false;
        }
        if (item == POWER_OFF_ALL) {
            return true;
        }
        return !item.equals(activePlayer);
    }

    public boolean continuePlayback() {
        return continuePlayback;
    }
}
