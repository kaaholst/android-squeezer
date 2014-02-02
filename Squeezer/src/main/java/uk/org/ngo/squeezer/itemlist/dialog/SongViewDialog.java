package uk.org.ngo.squeezer.itemlist.dialog;

import android.content.Context;

import uk.org.ngo.squeezer.R;
import uk.org.ngo.squeezer.model.Song;
import uk.org.ngo.squeezer.service.ServerString;

public class SongViewDialog extends BaseViewDialog<Song, SongViewDialog.SongListLayout, SongViewDialog.SongsSortOrder> {

    @Override
    protected String getTitle() {
        return getString(R.string.song_display_options);
    }

    /**
     * Supported song list layouts.
     */
    public enum SongListLayout implements BaseViewDialog.EnumWithTextAndIcon {
        grid(R.drawable.ic_action_view_as_grid, ServerString.SWITCH_TO_GALLERY),
        list(R.drawable.ic_action_view_as_list, ServerString.SWITCH_TO_EXTENDED_LIST);

        /**
         * The icon to use for this layout
         */
        private int icon;

        @Override
        public int getIcon() {
            return icon;
        }

        /**
         * The text to use for this layout
         */
        private ServerString serverString;

        @Override
        public String getText(Context context) {
            return serverString.getLocalizedString();
        }

        SongListLayout(int icon, ServerString serverString) {
            this.serverString = serverString;
            this.icon = icon;
        }

    }

    /**
     * Sort order strings supported by the server.
     * <p/>
     * Values must correspond with the string expected by the server. Any '__' in the strings will
     * be removed.
     */
    public enum SongsSortOrder implements BaseViewDialog.EnumWithText {
        title(R.string.songs_sort_order_title),
        tracknum(R.string.songs_sort_order_tracknum);
        // TODO: At least some versions of the server support "albumtrack",
        // is that useful?

        /**
         * The text to use for this ordering
         */
        private int stringResource;

        @Override
        public String getText(Context context) {
            return context.getText(stringResource).toString();
        }

        private SongsSortOrder(int stringResource) {
            this.stringResource = stringResource;
        }
    }

}
