/*
 * Copyright (c) 2022 Kurt Aaholst <kaaholst@gmail.com>
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

package uk.org.ngo.squeezer.util;

import java.text.DateFormatSymbols;

public class TimeUtil {

    public static String timeFormat(int hour, int minute, boolean is24HourFormat) {
        String timeFormat = is24HourFormat ? "%02d:%02d" : "%d:%02d";
        int displayHour = hour;
        if (!is24HourFormat) {
            displayHour = displayHour % 12;
            if (displayHour == 0) displayHour = 12;
        }

        return String.format(timeFormat, displayHour, minute);
    }

    public static String formatAmPm(int hour) {
        String[] amPmStrings = new DateFormatSymbols().getAmPmStrings();
        String am = amPmStrings[0];
        String pm = amPmStrings[1];

        return (hour < 12 ? am : pm);
    }
}
