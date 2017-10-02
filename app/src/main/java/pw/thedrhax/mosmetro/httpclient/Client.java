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
import android.os.SystemClock;
import android.support.annotation.NonNull;

import java.io.IOException;
import java.io.InputStream;
import java.util.HashMap;
import java.util.Map;

import pw.thedrhax.util.Listener;
import pw.thedrhax.util.Randomizer;
import pw.thedrhax.util.Util;

public abstract class Client {
    public static final String HEADER_ACCEPT = "Accept";
    public static final String HEADER_ACCEPT_LANGUAGE = "Accept-Language";
    public static final String HEADER_USER_AGENT = "User-Agent";
    public static final String HEADER_REFERER = "Referer";
    public static final String HEADER_CSRF = "X-CSRF-Token";
    public static final String HEADER_LOCATION = "Location";
    public static final String HEADER_UPGRADE_INSECURE_REQUESTS = "Upgrade-Insecure-Requests";
    public static final String HEADER_DO_NOT_TRACK = "DNT";

    protected Map<String,String> headers;
    protected Context context;
    protected Randomizer random;
    protected boolean random_delays = false;
    protected ParsedResponse last_response = new ParsedResponse(this, "", "", 200, null);

    protected Client(Context context) {
        this.context = context;
        this.headers = new HashMap<>();
        this.random = new Randomizer(context);
    }

    // Settings methods
    public abstract Client trustAllCerts();
    public abstract Client followRedirects(boolean follow);

    public Client configure() {
        setTimeout(Util.getIntPreference(context, "pref_timeout", 5) * 1000);

        setHeader(HEADER_USER_AGENT, random.cached_useragent());
        setHeader(HEADER_ACCEPT,
                "text/html,application/xhtml+xml," +
                "application/xml;q=0.9,image/webp," +
                "image/apng,*/*;q=0.8"
        );
        setHeader(HEADER_UPGRADE_INSECURE_REQUESTS, "1");
        setHeader(HEADER_DO_NOT_TRACK, "1");
        setHeader(HEADER_ACCEPT_LANGUAGE, "ru-RU,ru;q=0.9,en-US;q=0.8,en;q=0.7");

        return this;
    }

    public Client setHeader (String name, String value) {
        headers.put(name, value); return this;
    }

    public String getHeader (String name) {
        return headers.containsKey(name) ? headers.get(name) : null;
    }

    public Client resetHeaders () {
        headers = new HashMap<>(); return this;
    }

    public Client setDelaysEnabled(boolean enabled) {
        this.random_delays = enabled; return this;
    }

    public abstract Client setCookie(String url, String name, String value);
    public abstract Map<String,String> getCookies(String url);

    public abstract Client setTimeout(int ms);

    // IO methods
    public abstract ParsedResponse get(String link, Map<String,String> params) throws IOException;
    public abstract ParsedResponse post(String link, Map<String,String> params) throws IOException;
    public abstract InputStream getInputStream(String link) throws IOException;

    @NonNull
    public ParsedResponse response() {
        return last_response;
    }

    // Retry methods
    public ParsedResponse get(final String link, final Map<String,String> params,
                      int retries) throws IOException {
        return new RetryOnException<ParsedResponse>() {
            @Override
            public ParsedResponse body() throws IOException {
                random.delay(running);
                return get(link, params);
            }
        }.run(retries);
    }
    public ParsedResponse post(final String link, final Map<String,String> params,
                       int retries) throws IOException {
        return new RetryOnException<ParsedResponse>() {
            @Override
            public ParsedResponse body() throws IOException {
                random.delay(running);
                return post(link, params);
            }
        }.run(retries);
    }
    public InputStream getInputStream(final String link, int retries) throws IOException {
        return new RetryOnException<InputStream>() {
            @Override
            public InputStream body() throws IOException {
                return getInputStream(link);
            }
        }.run(retries);
    }

    // Cancel current request
    public abstract void stop();

    // Convert methods
    protected static String requestToString (Map<String,String> params) {
        StringBuilder params_string = new StringBuilder();

        if (params != null)
            for (Map.Entry<String,String> entry : params.entrySet())
                params_string
                        .append(params_string.length() == 0 ? "?" : "&")
                        .append(entry.getKey())
                        .append("=")
                        .append(entry.getValue());

        return params_string.toString();
    }

    protected final Listener<Boolean> running = new Listener<Boolean>(true) {
        @Override
        public void onChange(Boolean new_value) {
            if (!new_value) {
                stop();
            }
        }
    };

    public Client setRunningListener(Listener<Boolean> master) {
        running.subscribe(master); return this;
    }

    private abstract class RetryOnException<T> {
        T run(int retries) throws IOException {
            IOException last_ex = null;
            for (int i = 0; i < retries; i++) {
                try {
                    return body();
                } catch (IOException ex) {
                    last_ex = ex;
                    if (running.get()) {
                        SystemClock.sleep(1000);
                    } else {
                        break;
                    }
                }
            }
            if (last_ex != null) {
                throw last_ex;
            } else {
                throw new IOException("Unknown exception (retries=" + retries + ")");
            }
        }
        public abstract T body() throws IOException;
    }
}
