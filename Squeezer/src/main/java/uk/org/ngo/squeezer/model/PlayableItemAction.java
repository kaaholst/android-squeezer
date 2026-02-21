package uk.org.ngo.squeezer.model;

import android.content.Context;

import uk.org.ngo.squeezer.framework.EnumWithText;

public enum PlayableItemAction implements EnumWithText {
    PLAY(JiveItem.PLAY_NOW) {
        @Override
        public Action action(JiveItem item) {
            return item.playAction;
        }
    },
    ADD_TO_END(JiveItem.ADD_TO_END) {
        @Override
        public Action action(JiveItem item) {
            return item.addAction;
        }
    },
    PLAY_NEXT(JiveItem.PLAY_NEXT) {
        @Override
        public Action action(JiveItem item) {
            return item.insertAction;
        }
    };

    private final String text;

    PlayableItemAction(JiveItem item) {
        this.text = item.getName();
    }

    @Override
    public String getText(Context context) {
        return text;
    }

    public abstract Action action(JiveItem item);
}
