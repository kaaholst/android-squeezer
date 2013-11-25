/*
 * Copyright (c) 2011 Kurt Aaholst <kaaholst@gmail.com>
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package uk.org.ngo.squeezer.itemlists;

import java.util.EnumSet;

import uk.org.ngo.squeezer.R;
import uk.org.ngo.squeezer.Util;
import uk.org.ngo.squeezer.framework.SqueezerItem;
import uk.org.ngo.squeezer.framework.SqueezerItemView;
import uk.org.ngo.squeezer.framework.SqueezerPlaylistItem;
import uk.org.ngo.squeezer.itemlists.GenreSpinner.GenreSpinnerCallback;
import uk.org.ngo.squeezer.itemlists.YearSpinner.YearSpinnerCallback;
import uk.org.ngo.squeezer.itemlists.dialogs.SqueezerSongFilterDialog;
import uk.org.ngo.squeezer.itemlists.dialogs.SqueezerSongOrderDialog;
import uk.org.ngo.squeezer.itemlists.dialogs.SqueezerSongOrderDialog.SongsSortOrder;
import uk.org.ngo.squeezer.menu.MenuFragment;
import uk.org.ngo.squeezer.menu.SqueezerFilterMenuItemFragment;
import uk.org.ngo.squeezer.menu.SqueezerOrderMenuItemFragment;
import uk.org.ngo.squeezer.model.SqueezerAlbum;
import uk.org.ngo.squeezer.model.SqueezerArtist;
import uk.org.ngo.squeezer.model.SqueezerGenre;
import uk.org.ngo.squeezer.model.SqueezerSong;
import uk.org.ngo.squeezer.model.SqueezerYear;

import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.os.RemoteException;
import android.text.TextUtils;
import android.util.Log;
import android.view.ContextMenu;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ImageView;
import android.widget.Spinner;
import android.widget.TextView;

public class SqueezerSongListActivity extends SqueezerAbstractSongListActivity
        implements GenreSpinnerCallback, YearSpinnerCallback,
        SqueezerFilterMenuItemFragment.SqueezerFilterableListActivity,
        SqueezerOrderMenuItemFragment.SqueezerOrderableListActivity {
    private static final String TAG = SqueezerSongListActivity.class.getSimpleName();

    private SongsSortOrder sortOrder = SongsSortOrder.title;

    private String searchString;
    public String getSearchString() { return searchString; }
    public void setSearchString(String searchString) { this.searchString = searchString; }

    private SqueezerAlbum album;
    public SqueezerAlbum getAlbum() { return album; }
    public void setAlbum(SqueezerAlbum album) { this.album = album; }

    private SqueezerArtist artist;
    public SqueezerArtist getArtist() { return artist; }
    public void setArtist(SqueezerArtist artist) { this.artist = artist; }

	private SqueezerYear year;
    @Override
    public SqueezerYear getYear() { return year; }
    @Override
    public void setYear(SqueezerYear year) { this.year = year; }

	private SqueezerGenre genre;
    @Override
    public SqueezerGenre getGenre() { return genre; }
    @Override
    public void setGenre(SqueezerGenre genre) { this.genre = genre; }

	private GenreSpinner genreSpinner;
	public void setGenreSpinner(Spinner spinner) {
	    genreSpinner = new GenreSpinner(this, this, spinner);
	}

	private YearSpinner yearSpinner;
	public void setYearSpinner(Spinner spinner) {
	    yearSpinner = new YearSpinner(this, this, spinner);
	}

	private SqueezerSongView songViewLogic;

    private MenuItem playButton;
    private MenuItem addButton;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // Set the album header.
        if (album != null) {
            TextView albumView = (TextView) findViewById(R.id.albumname);
            TextView artistView = (TextView) findViewById(R.id.artistname);
            TextView yearView = (TextView) findViewById(R.id.yearname);
            ImageView btnContextMenu = (ImageView) findViewById(R.id.context_menu);

            albumView.setText(album.getName());
            artistView.setText(album.getArtist());
            if (album.getYear() != 0) {
                yearView.setText(Integer.toString(album.getYear()));
            }

            btnContextMenu.setOnCreateContextMenuListener(this);

            btnContextMenu.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    v.showContextMenu();
                }
            });
        }

        if (artist != null) {
            TextView header = (TextView) findViewById(R.id.header);
            header.setVisibility(View.VISIBLE);
            header.setText(getString(R.string.songs_by_header, artist.getName()));
        }

        // Adapter has been created (or restored from the fragment) by this point,
        // so fetch the itemView that was used.
        songViewLogic = (SqueezerSongView) getItemAdapter().getItemView();

        MenuFragment.add(this, SqueezerFilterMenuItemFragment.class);
        MenuFragment.add(this, SqueezerOrderMenuItemFragment.class);

        songViewLogic.setBrowseByAlbum(album != null);
        songViewLogic.setBrowseByArtist(artist != null);
    }

    @Override
    protected int getContentView() {
        Bundle extras = getIntent().getExtras();
        if (extras != null) {
            for (String key : extras.keySet()) {
                if (SqueezerAlbum.class.getName().equals(key)) {
                    album = extras.getParcelable(key);
                    sortOrder = SongsSortOrder.tracknum;
                } else if (SqueezerArtist.class.getName().equals(key)) {
                    artist = extras.getParcelable(key);
                } else if (SqueezerYear.class.getName().equals(key)) {
                    year = extras.getParcelable(key);
                } else if (SqueezerGenre.class.getName().equals(key)) {
                    genre = extras.getParcelable(key);
                } else
                    Log.e(getTag(), "Unexpected extra value: " + key + "("
                            + extras.get(key).getClass().getName() + ")");
            }
        }

        if (album != null) {
            return R.layout.item_list_album;
        }

        return super.getContentView();
    }

    @Override
    protected void onServiceConnected() {
        super.onServiceConnected();

        // Set artwork that requires a service connection.
        if (album != null) {
            ImageView artwork = (ImageView) findViewById(R.id.album);

            String artworkUrl = ((SqueezerSongView) getItemView()).getAlbumArtUrl(album.getArtwork_track_id());

            if (artworkUrl == null) {
                artwork.setImageResource(R.drawable.icon_album_noart);
            } else {
                getImageFetcher().loadImage(artworkUrl, artwork);
            }
        }
    }

    public static void show(Context context, SqueezerItem... items) {
	    final Intent intent = new Intent(context, SqueezerSongListActivity.class);
        for (SqueezerItem item: items)
        	intent.putExtra(item.getClass().getName(), item);
	    context.startActivity(intent);
	}


    @Override
    public SqueezerItemView<SqueezerSong> createItemView() {
        if (album != null) {
            songViewLogic = new SqueezerSongView(this);
            songViewLogic.setDetails(EnumSet.of(
                    SqueezerSongView.Details.TRACK_NO,
                    SqueezerSongView.Details.DURATION,
                    SqueezerSongView.Details.ARTIST_IF_COMPILATION));
        } else if (artist != null) {
            songViewLogic = new SongViewWithArt(this);
            songViewLogic.setDetails(EnumSet.of(
                    SqueezerSongView.Details.DURATION,
                    SqueezerSongView.Details.ALBUM,
                    SqueezerSongView.Details.YEAR
            ));
        } else {
            songViewLogic = new SongViewWithArt(this);
            songViewLogic.setDetails(EnumSet.of(
                    SqueezerSongView.Details.ARTIST,
                    SqueezerSongView.Details.ALBUM,
                    SqueezerSongView.Details.YEAR));
        }

        return songViewLogic;
    }

	@Override
	protected void registerCallback() throws RemoteException {
		super.registerCallback();
		if (genreSpinner != null) genreSpinner.registerCallback();
		if (yearSpinner != null) yearSpinner.registerCallback();
	}

    @Override
    protected void unregisterCallback() throws RemoteException {
        super.unregisterCallback();
		if (genreSpinner != null) genreSpinner.unregisterCallback();
		if (yearSpinner != null) yearSpinner.unregisterCallback();
	}

    @Override
    protected void orderPage(int start) throws RemoteException {
        getService().songs(start, sortOrder.name(), searchString, album, artist, year, genre);

        boolean canPlay = (getCurrentPlaylistItem() != null);
        if (playButton != null)
            playButton.setVisible(canPlay);

        if (addButton != null)
            addButton.setVisible(canPlay);
    }

    public SongsSortOrder getSortOrder() {
        return sortOrder;
    }

	public void setSortOrder(SongsSortOrder sortOrder) {
		this.sortOrder = sortOrder;
        clearAndReOrderItems();
	}

    @Override
    public void showFilterDialog() {
        new SqueezerSongFilterDialog().show(getSupportFragmentManager(), "SongFilterDialog");
    }

    @Override
    public void showOrderDialog() {
        new SqueezerSongOrderDialog().show(this.getSupportFragmentManager(), "OrderDialog");
    }

    @Override
    public void onCreateContextMenu(ContextMenu menu, View v, ContextMenu.ContextMenuInfo menuInfo) {
        super.onCreateContextMenu(menu, v, menuInfo);

        if (album != null) {
            MenuInflater menuInflater = getMenuInflater();
            menuInflater.inflate(R.menu.albumcontextmenu, menu);

            // Hide the option to view the album.
            MenuItem browse = menu.findItem(R.id.browse_songs);
            if (browse != null) {
                browse.setVisible(false);
            }
        }
    }

    @Override
    public boolean onContextItemSelected(MenuItem item) {
        AdapterView.AdapterContextMenuInfo info = (AdapterView.AdapterContextMenuInfo) item.getMenuInfo();

        // If info is null then this the context menu from the header, not a list item.
        if (info == null) {
            try {
                switch (item.getItemId()) {
                    case R.id.play_now:
                        play(album);
                        return true;

                    case R.id.add_to_playlist:
                        add(album);
                        return true;

                    case R.id.browse_artists:
                        SqueezerArtistListActivity.show(this, album);
                        return true;

                    default:
                        throw new IllegalStateException("Unknown menu ID.");
                }
            } catch (RemoteException e) {
                Log.e(getTag(), "Error executing menu action '" + item.getTitle() + "': " + e);
            }

        }
        return super.onContextItemSelected(item);
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        // Only show the play entries from the options menu for albums (the context menu already
        // shows them).
        if (album == null) {
            getMenuInflater().inflate(R.menu.playmenu, menu);
            playButton = getActionBarHelper().findItem(R.id.play_now);
            addButton = getActionBarHelper().findItem(R.id.add_to_playlist);
        }
        return super.onCreateOptionsMenu(menu);
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        try {
            SqueezerPlaylistItem playlistItem = getCurrentPlaylistItem();
            switch (item.getItemId()) {
            case R.id.play_now:
                if (playlistItem != null)
                    play(playlistItem);
                return true;
            case R.id.add_to_playlist:
                if (playlistItem != null)
                    add(playlistItem);
                return true;
            }
        } catch (RemoteException e) {
            Log.e(getTag(), "Error executing menu action '" + item.getMenuInfo() + "': " + e);
        }
        return super.onOptionsItemSelected(item);
    }
    
    private SqueezerPlaylistItem getCurrentPlaylistItem() {
        int playlistItems = Util.countBooleans(album != null, artist != null, genre != null, year != null);
        if (playlistItems == 1 && TextUtils.isEmpty(searchString)) {
            if (album != null) return album;
            if (artist != null) return artist;
            if (genre != null) return genre;
            if (year != null) return year;
        }
        return null;
    }

}
