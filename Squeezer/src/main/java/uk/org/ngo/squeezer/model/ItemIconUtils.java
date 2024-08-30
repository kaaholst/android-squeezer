/*
 * Copyright (c) 2023 Kurt Aaholst <kaaholst@gmail.com>
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

package uk.org.ngo.squeezer.model;

import android.content.Context;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.LayerDrawable;
import android.os.Build;
import android.text.TextUtils;
import android.util.Log;
import android.view.Gravity;

import androidx.annotation.DrawableRes;
import androidx.appcompat.content.res.AppCompatResources;
import androidx.core.graphics.drawable.DrawableCompat;

import java.util.HashMap;
import java.util.Map;
import java.util.function.BiPredicate;

import uk.org.ngo.squeezer.R;
import uk.org.ngo.squeezer.Squeezer;
import uk.org.ngo.squeezer.util.FluentHashMap;


public class ItemIconUtils {

    /**
     * @return Icon resource for this item if it is embedded in the Squeezer app, or the supplied default icon.
     */
    public static Drawable getIconDrawable(Context context, JiveItem item, @DrawableRes int defaultIcon) {
        @DrawableRes Integer itemIcon = getItemIcon(item);
        Drawable icon = AppCompatResources.getDrawable(context, itemIcon != null ? itemIcon : defaultIcon);

        if (Squeezer.getPreferences().useFlatIcons()) {
            return icon;
        }

        // If the preference is to use legacy icons, add a background the item icon
        int inset = context.getResources().getDimensionPixelSize(R.dimen.album_art_inset);
        Drawable background = AppCompatResources.getDrawable(context, R.drawable.icon_background);
        Drawable wrappedIcon = DrawableCompat.wrap(icon.mutate());
        DrawableCompat.setTint(wrappedIcon, context.getResources().getColor(R.color.black));
        LayerDrawable layerDrawable = new LayerDrawable(new Drawable[]{background, wrappedIcon});
        layerDrawable.setLayerInset(1, inset, inset, inset, inset);

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            layerDrawable.setLayerGravity(1, Gravity.CENTER);
        }
        return layerDrawable;
    }

    private static String iconStyle(JiveItem item) {
        return TextUtils.isEmpty(item.iconStyle) ? "hm_" + item.getId() :item. iconStyle;
    }

    @DrawableRes static public Integer getItemIcon(JiveItem item) {
        Integer itemIcon = itemIcons.get(iconStyle(item));
        if (itemIcon == null && Squeezer.getPreferences().useFlatIcons()) {
            String path = item.getIcon().getPath();
            Log.d("GetIcon", "path: " + path);
            if (path != null) {
                if (path.startsWith("/imageproxy/") && path.endsWith("/image.png")) {
                    path = path.substring(0, path.length() - "/image.png".length());
                }
                for (Map.Entry<String, IconMap> entry : iconMappings.entrySet()) {
                    IconMap iconMap = entry.getValue();
                    if (iconMap.predicate.test(path, entry.getKey())) return iconMap.iconRes;
                }
            }
        }
        return itemIcon;
    }

    private static final Map<String, Integer> itemIcons = initializeItemIcons();

    private static Map<String, Integer> initializeItemIcons() {
        Map<String, Integer> result = new HashMap<>();

        result.put("hm_myMusic", R.drawable.library_music);
        result.put("hm_extras", R.drawable.settings_advanced);
        result.put("hm_settings", R.drawable.settings);
        result.put("hm_opmlmyapps", R.drawable.apps);
        result.put("hm_opmlappgallery", R.drawable.apps_settings);
        result.put("hm_settingsAlarm", R.drawable.alarm_clock);
        result.put("hm_appletCustomizeHome", R.drawable.settings_home);
        result.put("hm_settingsPlayerNameChange", R.drawable.rename);
        result.put("hm_advancedSettings", R.drawable.settings_advanced);

        result.put("hm_archiveNode", R.drawable.ic_archive);
        result.put("hm_radio", R.drawable.internet_radio);
        result.put("hm_radios", R.drawable.internet_radio);
        result.put("hm_favorites", R.drawable.favorites);
        result.put("hm_globalSearch", R.drawable.search);
        result.put("hm_homeSearchRecent", R.drawable.search);
        result.put("hm_playerpower", R.drawable.power);
        result.put("hm_myMusicSearch", R.drawable.search);
        result.put("hm_myMusicSearchArtists", R.drawable.search);
        result.put("hm_myMusicSearchAlbums", R.drawable.search);
        result.put("hm_myMusicSearchSongs", R.drawable.search);
        result.put("hm_myMusicSearchPlaylists", R.drawable.search);
        result.put("hm_myMusicSearchRecent", R.drawable.search);
        result.put("hm_myMusicArtists", R.drawable.ml_artists);
        result.put("hm_myMusicArtistsAlbumArtists", R.drawable.ml_artists_album);
        result.put("hm_myMusicArtistsAllArtists", R.drawable.ml_artists);
        result.put("hm_myMusicArtistsComposers", R.drawable.ml_artists_composer);
        result.put("hm_myMusicAlbums", R.drawable.ml_albums);
        result.put("hm_myMusicAlbumsVariousArtists", R.drawable.ml_albums);
        result.put("hm_myMusicGenres", R.drawable.ml_genres);
        result.put("hm_myMusicYears", R.drawable.ml_years);
        result.put("hm_myMusicMusicFolder", R.drawable.ml_folder);
        result.put("hm_myMusicPlaylists", R.drawable.ml_playlist);
        result.put("hm_myMusicNewMusic", R.drawable.ml_new_music);
        result.put("hm_myMusicWorks", R.drawable.works);
        result.put("hm_randomplay", R.drawable.ml_random);
        result.put("hm_opmlselectVirtualLibrary", R.drawable.ml_library_views);
        result.put("hm_opmlselectRemoteLibrary", R.drawable.library_music);
        result.put("hm_settingsShuffle", R.drawable.shuffle);
        result.put("hm_settingsRepeat", R.drawable.settings_repeat);
        result.put("hm_settingsAudio", R.drawable.settings_audio);
        result.put("hm_settingsSleep", R.drawable.settings_sleep);
        result.put("hm_settingsSync", R.drawable.settings_sync);
        result.put("hm_settingsBrightness", R.drawable.settings_brightness);
        result.put("hm_settingsLineInLevel", R.drawable.icon_line_in);
        result.put("hm_settingsLineInAlwaysOn", R.drawable.icon_line_in);
        result.put("hm_settingsDontStopTheMusic", R.drawable.setting_dont_stop_the_music);
//      TODO: Make unique icon for custom shortcut, or load icon from original slim item or its parents

        return result;
    }

    private static final Map<String, IconMap> iconMappings = new FluentHashMap<String, IconMap>()
            .with("/deezer.png", new IconMap(ItemIconUtils::endsWith, R.drawable.deezer))
            .with("/Deezer/html/images/icon.png", new IconMap(ItemIconUtils::endsWith, R.drawable.deezer))
            .with("/tidal.png", new IconMap(ItemIconUtils::endsWith, R.drawable.tidal))
            .with("/WiMP/html/images/icon.png", new IconMap(ItemIconUtils::endsWith, R.drawable.tidal))
            .with("/plugins/WiMP/html/images/icon.png", new IconMap(ItemIconUtils::endsWith, R.drawable.tidal))
            .with("/plugins/Spotty/html/images/[0-9a-fA-F]*[.]png", new IconMap(ItemIconUtils::matches, R.drawable.spotify))
            .with("Spotty/html/images/transfer.png", new IconMap(ItemIconUtils::endsWith, R.drawable.nowplaying))
            .with("/qobuz.png", new IconMap(ItemIconUtils::endsWith, R.drawable.qobuz))
            .with("/Bandcamp/html/images/logo.png", new IconMap(ItemIconUtils::endsWith, R.drawable.bandcamp))
            .with("/tuneinradio.png", new IconMap(ItemIconUtils::endsWith, R.drawable.tunein))
            .with("/youtube.png", new IconMap(ItemIconUtils::endsWith, R.drawable.youtube))
            .with("/icon_nature_sounds.png", new IconMap(ItemIconUtils::endsWith, R.drawable.nature_sounds))
            .with("/plugins/Sounds/html/images/icon.png", new IconMap(ItemIconUtils::endsWith, R.drawable.nature_sounds))
            .with("/napster.png", new IconMap(ItemIconUtils::endsWith, R.drawable.napster))
            .with("/plugins/RhapsodyDirect/html/images/icon.png", new IconMap(ItemIconUtils::endsWith, R.drawable.napster))
            .with("/plugins/Slacker/html/images/icon.png", new IconMap(ItemIconUtils::endsWith, R.drawable.slacker))
            .with("/pandora.png", new IconMap(ItemIconUtils::endsWith, R.drawable.pandora))
            .with("/plugins/Pandora/html/images/icon.png", new IconMap(ItemIconUtils::endsWith, R.drawable.pandora))
            .with("/SqueezeCloud/html/images/icon.png", new IconMap(ItemIconUtils::endsWith, R.drawable.soundcloud))
            .with("/podcast.png", new IconMap(ItemIconUtils::endsWith, R.drawable.podcasts))
            .with("/podcasts.png", new IconMap(ItemIconUtils::endsWith, R.drawable.podcasts))
            .with("/somafm.png", new IconMap(ItemIconUtils::endsWith, R.drawable.somafm))
            .with("/lma.png", new IconMap(ItemIconUtils::endsWith, R.drawable.lma))
            .with("/plugins/LMA/html/images/icon.png", new IconMap(ItemIconUtils::endsWith, R.drawable.lma))
            .with("/static/images/icons/classical.png", new IconMap(ItemIconUtils::endsWith, R.drawable.classical_com))
            .with("/plugins/Classical/html/images/icon.png", new IconMap(ItemIconUtils::endsWith, R.drawable.classical_com))
            .with("/plugins/WalkWithMe/html/images/icon.png", new IconMap(ItemIconUtils::endsWith, R.drawable.ml_playlist))
            .with("/picks.png", new IconMap(ItemIconUtils::endsWith, R.drawable.staff_picks))
            .with("/radiopresets.png", new IconMap(ItemIconUtils::endsWith, R.drawable.favorites))
            .with("/radiolocal.png", new IconMap(ItemIconUtils::endsWith, R.drawable.location))
            .with("/radiomusic.png", new IconMap(ItemIconUtils::endsWith, R.drawable.radio_music))
            .with("/radiosports.png", new IconMap(ItemIconUtils::endsWith, R.drawable.radio_sports))
            .with("/radionews.png", new IconMap(ItemIconUtils::endsWith, R.drawable.radio_news))
            .with("/radiotalk.png", new IconMap(ItemIconUtils::endsWith, R.drawable.radio_talk))
            .with("/radioworld.png", new IconMap(ItemIconUtils::endsWith, R.drawable.earth))
            .with("/radiosearch.png", new IconMap(ItemIconUtils::endsWith, R.drawable.search))
            .with("/rss.png", new IconMap(ItemIconUtils::endsWith, R.drawable.rss))
            .with("/flow.png", new IconMap(ItemIconUtils::endsWith, R.drawable.setting_dont_stop_the_music))
            .with("/smart_radio.png", new IconMap(ItemIconUtils::endsWith, R.drawable.setting_dont_stop_the_music))
            .with("/focus.png", new IconMap(ItemIconUtils::endsWith, R.drawable.target))
            .with("/motivation.png", new IconMap(ItemIconUtils::endsWith, R.drawable.emoticon_excited_outline))
            .with("/chill.png", new IconMap(ItemIconUtils::endsWith, R.drawable.emoticon_cool_outline))
            .with("/melancholy.png", new IconMap(ItemIconUtils::endsWith, R.drawable.emoticon_sad_outline))
            .with("/you_and_me.png", new IconMap(ItemIconUtils::endsWith, R.drawable.favorites))
            .with("/moods_MTL_icon_celebration.png", new IconMap(ItemIconUtils::endsWith, R.drawable.party))
            // Fallbacks if none of the above matches
            .with("/home.png", new IconMap(ItemIconUtils::endsWith, R.drawable.ic_action_home))
            .with("/search.png", new IconMap(ItemIconUtils::endsWith, R.drawable.search))
            .with("/settings.png", new IconMap(ItemIconUtils::endsWith, R.drawable.settings))
            .with("/history.png", new IconMap(ItemIconUtils::endsWith, R.drawable.history))
            .with("/artist.png", new IconMap(ItemIconUtils::endsWith, R.drawable.ml_artists))
            .with("/artists.png", new IconMap(ItemIconUtils::endsWith, R.drawable.ml_artists))
            .with("/album.png", new IconMap(ItemIconUtils::endsWith, R.drawable.ml_albums))
            .with("/albums.png", new IconMap(ItemIconUtils::endsWith, R.drawable.ml_albums))
            .with("/song.png", new IconMap(ItemIconUtils::endsWith, R.drawable.ml_songs))
            .with("/songs.png", new IconMap(ItemIconUtils::endsWith, R.drawable.ml_songs))
            .with("/playall.png", new IconMap(ItemIconUtils::endsWith, R.drawable.ml_songs))
            .with("/musicfolder.png", new IconMap(ItemIconUtils::endsWith, R.drawable.ml_folder))
            .with("/personal.png", new IconMap(ItemIconUtils::endsWith, R.drawable.person))
            .with("/favorites.png", new IconMap(ItemIconUtils::endsWith, R.drawable.favorites))
            .with("/toptracks.png", new IconMap(ItemIconUtils::endsWith, R.drawable.charts))
            .with("/party.png", new IconMap(ItemIconUtils::endsWith, R.drawable.party))
            .with("/chart.png", new IconMap(ItemIconUtils::endsWith, R.drawable.charts))
            .with("/charts.png", new IconMap(ItemIconUtils::endsWith, R.drawable.charts))
            .with("/news.png", new IconMap(ItemIconUtils::endsWith, R.drawable.ml_new_music))
            .with("/inbox.png", new IconMap(ItemIconUtils::endsWith, R.drawable.ml_playlist))
            .with("/playlist.png", new IconMap(ItemIconUtils::endsWith, R.drawable.ml_playlist))
            .with("/playlists.png", new IconMap(ItemIconUtils::endsWith, R.drawable.ml_playlist))
            .with("/radio.png", new IconMap(ItemIconUtils::endsWith, R.drawable.internet_radio))
            .with("/genres.png", new IconMap(ItemIconUtils::endsWith, R.drawable.ml_genres))
            .with("/works.png", new IconMap(ItemIconUtils::endsWith, R.drawable.works))
            ;


    private static class IconMap {
        private final BiPredicate<String, String> predicate;
        private final @DrawableRes int iconRes;

        public IconMap(BiPredicate<String, String> predicate, int iconRes) {
            this.predicate = predicate;
            this.iconRes = iconRes;
        }
    }

    private static boolean endsWith(String self, String key) {
        return self.endsWith(key);
    }

    private static boolean matches(String self, String key) {
        return self.matches(key);
    }
}
