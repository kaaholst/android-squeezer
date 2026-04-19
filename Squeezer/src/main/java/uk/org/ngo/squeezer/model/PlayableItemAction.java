package uk.org.ngo.squeezer.model;

import android.content.Context;

import androidx.annotation.StringRes;

import uk.org.ngo.squeezer.R;
import uk.org.ngo.squeezer.framework.EnumWithText;

public enum PlayableItemAction implements EnumWithText {
    NONE(R.string.no_action) {
        @Override
        public Action action(JiveItem item) {
            return null;
        }
    },
    PLAY(R.string.PLAY_NOW) {
        @Override
        public Action action(JiveItem item) {
            return item.playAction;
        }
    },
    ADD_TO_END(R.string.ADD_TO_END) {
        @Override
        public Action action(JiveItem item) {
            return item.addAction;
        }
    },
    PLAY_NEXT(R.string.PLAY_NEXT) {
        @Override
        public Action action(JiveItem item) {
            return item.insertAction;
        }
    };

    @StringRes
    private final int text;

    PlayableItemAction(@StringRes int text) {
        this.text = text;
    }

    @Override
    public String getText(Context context) {
        return context.getString(text);
    }

    public abstract Action action(JiveItem item);
}
