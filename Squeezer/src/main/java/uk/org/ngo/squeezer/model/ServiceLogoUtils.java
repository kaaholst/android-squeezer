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

import androidx.annotation.DrawableRes;
import androidx.appcompat.content.res.AppCompatResources;

import java.util.HashMap;
import java.util.Map;

import uk.org.ngo.squeezer.R;


public class ServiceLogoUtils {

    public static Drawable getLogo(Context context, String extid) {
        @DrawableRes Integer logo = (extid != null ? serviceLogos.get(extid.split(":")[0]) : null);
        return logo != null ? AppCompatResources.getDrawable(context, logo) : null;
    }

    private static final Map<String, Integer> serviceLogos = initializeServiceLogos();

    private static Map<String, Integer> initializeServiceLogos() {
        Map<String, Integer> result = new HashMap<>();

        result.put("spotify", R.drawable.spotify_emblem);
        result.put("qobuz", R.drawable.qobuz_emblem);
        result.put("wimp", R.drawable.tidal_emblem);
        result.put("deezer", R.drawable.deezer_emblem);
        result.put("bandcamp", R.drawable.bandcamp_emblem);

        return result;
    }
}
