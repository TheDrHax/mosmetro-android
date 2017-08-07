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
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.preference.PreferenceManager;
import android.util.Base64;

import java.io.ByteArrayOutputStream;
import java.io.IOException;

public final class Util {
    private Util() {}

    // TODO: Store Integers instead of Strings in SharedPreferences
    public static int getIntPreference (Context context, String name, int def_value) {
        int result = def_value;
        try {
            result = Integer.parseInt(
                    PreferenceManager
                            .getDefaultSharedPreferences(context)
                            .getString(name, Integer.valueOf(def_value).toString())
            );
        } catch (NumberFormatException ignored) {}
        return result;
    }

    public static String bitmapToBase64(Bitmap bitmap) {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        bitmap.compress(Bitmap.CompressFormat.PNG, 100, baos);

        byte[] bytes = baos.toByteArray();
        try {
            baos.close();
        } catch (IOException ignored) {}
        return Base64.encodeToString(bytes, Base64.DEFAULT);
    }

    public static Bitmap base64ToBitmap(String base64) {
        byte[] bytes = Base64.decode(base64, Base64.DEFAULT);
        return BitmapFactory.decodeByteArray(bytes, 0, bytes.length);
    }

    public static String convertCyrillicSymbols(String s) {
        s = s.replaceAll("а", "a");
        s = s.replaceAll("б", "b");
        s = s.replaceAll("с", "c");
        s = s.replaceAll("д", "d");
        s = s.replaceAll("е", "e");
        s = s.replaceAll("ф", "f");
        return s;
    }
}
