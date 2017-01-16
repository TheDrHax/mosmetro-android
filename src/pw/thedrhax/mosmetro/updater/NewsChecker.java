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

package pw.thedrhax.mosmetro.updater;

import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.net.Uri;
import android.preference.PreferenceManager;
import android.support.annotation.NonNull;

import org.json.simple.JSONObject;
import org.json.simple.parser.JSONParser;
import org.json.simple.parser.ParseException;

import pw.thedrhax.mosmetro.BuildConfig;
import pw.thedrhax.mosmetro.R;
import pw.thedrhax.mosmetro.httpclient.CachedRetriever;
import pw.thedrhax.util.Notification;
import pw.thedrhax.util.Version;

public class NewsChecker {
    private final Context context;
    private final SharedPreferences settings;

    public NewsChecker(@NonNull Context context) {
        this.context = context;
        this.settings = PreferenceManager.getDefaultSharedPreferences(context);
    }

    public void check() {
        String content = new CachedRetriever(context).get(BuildConfig.NEWS_URL, 30*60, "{}");

        JSONObject data;
        try {
            data = (JSONObject) new JSONParser().parse(content);
        } catch (ParseException ex) {
            return;
        }

        long id, max_version;
        String title, message;
        Uri url;
        try {
            id = Integer.parseInt((String)data.get("id"));
            max_version = Integer.parseInt((String)data.get("max_version"));
            title = (String)data.get("title");
            message = (String)data.get("message");
            url = Uri.parse((String)data.get("url"));
        } catch (Exception ex) {
            return;
        }

        if (max_version < Version.getVersionCode())
            return;

        if (settings.getLong("pref_notify_news_id", 0) >= id)
            return;

        new Notification(context)
                .setCancellable(true)
                .setId(255)
                .setIcon(R.drawable.ic_notification_message)
                .setIntent(new Intent(Intent.ACTION_VIEW).setData(url))
                .setTitle(title)
                .setText(message)
                .show();

        settings.edit()
                .putLong("pref_notify_news_id", id)
                .apply();
    }
}
