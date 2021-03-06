/**
 * Wi-Fi в метро (pw.thedrhax.mosmetro, Moscow Wi-Fi autologin)
 * Copyright © 2015 Dmitry Karikh <the.dr.hax@gmail.com>
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */

package pw.thedrhax.util;

import android.content.Context;
import android.content.SharedPreferences;
import android.preference.PreferenceManager;

public class UUID {
    public static String get(Context context) {
        SharedPreferences settings = PreferenceManager.getDefaultSharedPreferences(context);
        String uuid = settings.getString("uuid", "none");
        if ("none".equals(uuid)) {
            uuid = java.util.UUID.randomUUID().toString();
            settings.edit().putString("uuid", uuid).apply();
        }
        return uuid;
    }
}
