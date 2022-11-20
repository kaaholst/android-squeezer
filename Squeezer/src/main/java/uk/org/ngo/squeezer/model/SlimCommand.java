/*
 * Copyright (c) 2020 Kurt Aaholst <kaaholst@gmail.com>
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

import android.os.Parcel;
import android.os.Parcelable;

import androidx.annotation.NonNull;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import uk.org.ngo.squeezer.Util;

/**
 * Data for a command to LMS
 */
public class SlimCommand implements Parcelable {
    /** Array of command terms, f.e. ['playlist', 'jump'] */
    public final List<String> cmd = new ArrayList<>();

    /** Hash of parameters, f.e. {sort = new}. Passed to the server in the form "key:value", f.e. 'sort:new'. */
    public final Map<String, Object> params = new HashMap<>();

    public SlimCommand() {
    }

    protected SlimCommand(Parcel in) {
        cmd(in.createStringArrayList());
        params(Util.mapify(in.createStringArray()));
    }

    @Override
    public void writeToParcel(Parcel dest, int flags) {
        dest.writeStringList(cmd);
        String[] tokens = new String[params.size()];
        int i = 0;
        for (Map.Entry entry : params.entrySet()) {
            tokens[i++] = entry.getKey() + ":" + entry.getValue();
        }
        dest.writeStringArray(tokens);
    }

    @Override
    public int describeContents() {
        return 0;
    }

    public static final Creator<SlimCommand> CREATOR = new Creator<>() {
        @Override
        public SlimCommand createFromParcel(Parcel in) {
            return new SlimCommand(in);
        }

        @Override
        public SlimCommand[] newArray(int size) {
            return new SlimCommand[size];
        }
    };

    public SlimCommand cmd(String... commandTerms) {
        return cmd(Arrays.asList(commandTerms));
    }

    public SlimCommand cmd(List<String> commandTerms) {
        cmd.addAll(commandTerms);
        return this;
    }

    public SlimCommand params(Map<String, Object> params) {
        this.params.putAll(params);
        return this;
    }

    public SlimCommand param(String tag, Object value) {
        params.put(tag, value);
        return this;
    }

    public String[] cmd() {
        return cmd.toArray(new String[0]);
    }

    @NonNull
    @Override
    public String toString() {
        return "Command{" +
                "cmd=" + cmd +
                ", params=" + params +
                '}';
    }
}