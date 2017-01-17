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

package pw.thedrhax.mosmetro.httpclient;

import android.content.Context;
import android.content.SharedPreferences;
import android.preference.PreferenceManager;

import org.json.simple.JSONArray;
import org.json.simple.JSONObject;
import org.json.simple.parser.JSONParser;
import org.json.simple.parser.ParseException;

import java.util.Collection;
import java.util.LinkedList;

import pw.thedrhax.mosmetro.httpclient.clients.OkHttp;

public class CachedRetriever {
    private SharedPreferences settings;
    private JSONArray cache_storage;
    private Client client;

    public CachedRetriever (Context context) {
        this.settings = PreferenceManager.getDefaultSharedPreferences(context);

        try {
            cache_storage = (JSONArray)new JSONParser().parse(settings.getString("CachedRetriever", "[]"));
        } catch (ParseException ex) {
            cache_storage = new JSONArray();
        }

        client = new OkHttp();
    }

    public CachedRetriever setClient (Client client) {
        this.client = client; return this;
    }

    private long getTimestamp () {
        return System.currentTimeMillis() / 1000L;
    }

    private JSONObject findCachedUrl (String url) {
        for (Object object : cache_storage) {
            JSONObject json = (JSONObject)object;

            if (json.get("url").equals(url)) return json;
        }
        return null;
    }

    public void remove(String url) {
        Collection<Object> remove = new LinkedList<Object>();
        for (Object object : cache_storage)
            if (((JSONObject)object).get("url").equals(url))
                remove.add(object);
        cache_storage.removeAll(remove);

        // Write cache to preferences
        settings.edit().putString("CachedRetriever", cache_storage.toString()).apply();
    }

    private void writeCachedUrl (String url, String content) {
        // Remove old entries
        remove(url);

        // Create new entry
        JSONObject result = new JSONObject();
        result.put("url", url);
        result.put("content", content);
        result.put("timestamp", getTimestamp());

        // Add entry to cache
        cache_storage.add(result);

        // Write cache to preferences
        settings.edit().putString("CachedRetriever", cache_storage.toString()).apply();
    }

    public String get (String url, int ttl, String default_value) {
        JSONObject cached_url = findCachedUrl(url);
        String result = null;

        // Get content from cache if it isn't expired
        if (cached_url != null) {
            long timestamp = (Long) cached_url.get("timestamp");
            if (timestamp + ttl > getTimestamp())
                return cached_url.get("content").toString();
        }

        // Try to retrieve content from server
        try {
            if (client.get(url, null).getResponseCode() != 200)
                throw new Exception("Response Code != 200");

            // Write new content to cache
            result = client.getPage().trim();
            writeCachedUrl(url, result);
        } catch (Exception ex) {
            // Get expired cache if can't retrieve content
            if (cached_url != null)
                result = cached_url.get("content").toString();
        }

        return result != null ? result : default_value;
    }

    public String get (String url, String default_value) {
        return get(url, 24*60*60, default_value);
    }
}
