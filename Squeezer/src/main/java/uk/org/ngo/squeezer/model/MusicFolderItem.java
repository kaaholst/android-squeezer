/*
 * Copyright (c) 2012 Google Inc.
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

import android.net.Uri;

import androidx.annotation.NonNull;

import java.util.Map;

import uk.org.ngo.squeezer.Util;

/**
 * Encapsulate a music folder item on the slimserver.
 * <p>
 * An item has a name and a type. The name is free text, the type may be one of "track", "folder",
 * "playlist", or "unknown".
 *
 * @author nik
 */
public class MusicFolderItem {

    public String id;
    public String name;

    /**
     * The folder item's type, "track", "folder", "playlist", "unknown".
     */
    // XXX: Should be an enum.
    public String type;

    @NonNull
    public Uri url;

    public String coverId;

    public MusicFolderItem(Map<String, Object> record) {
        id = Util.getString(record, "id");
        name = Util.getString(record, "filename");
        type = Util.getString(record, "type");
        url = Uri.parse(Util.getStringOrEmpty(record, "url"));
        coverId = Util.getString(record, "coverid");
    }

    @NonNull
    @Override
    public String toString() {
        return "MusicFolderItem{" +
                "id='" + id + '\'' +
                ", name='" + name + '\'' +
                ", type='" + type + '\'' +
                ", url=" + url +
                '}';
    }
}