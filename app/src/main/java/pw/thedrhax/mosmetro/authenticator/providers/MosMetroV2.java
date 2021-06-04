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

package pw.thedrhax.mosmetro.authenticator.providers;

import android.content.Context;
import android.net.Uri;
import androidx.annotation.NonNull;
import android.util.Patterns;

import org.json.simple.JSONObject;

import java.io.IOException;
import java.net.ProtocolException;
import java.text.ParseException;
import java.util.HashMap;

import pw.thedrhax.mosmetro.R;
import pw.thedrhax.mosmetro.authenticator.InitialConnectionCheckTask;
import pw.thedrhax.mosmetro.authenticator.InterceptorTask;
import pw.thedrhax.mosmetro.authenticator.NamedTask;
import pw.thedrhax.mosmetro.authenticator.Provider;
import pw.thedrhax.mosmetro.authenticator.Task;
import pw.thedrhax.mosmetro.httpclient.Client;
import pw.thedrhax.mosmetro.httpclient.Headers;
import pw.thedrhax.mosmetro.httpclient.HttpRequest;
import pw.thedrhax.mosmetro.httpclient.HttpResponse;
import pw.thedrhax.util.Logger;
import pw.thedrhax.util.Randomizer;

/**
 * The MosMetroV2 class implements support for auth.wi-fi.ru algorithm.
 *
 * Detection: Meta-redirect contains ".wi-fi.ru" with any 3rd level domain (except "login").
 *
 * When pref_mosmetro_v3 is disabled, welcome.wi-fi.ru will be handled and bypassed by MosMetroV2
 * in all regions except Saint Petersburg (see MosMetroV3 instead).
 *
 * @author Dmitry Karikh <the.dr.hax@gmail.com>
 * @see Provider
 */

public class MosMetroV2 extends Provider {
    private String redirect = "https://auth.wi-fi.ru/?segment=metro";

    // TODO: Split branches into sub-providers

    /**
     * Saint-Petersburg branch
     *
     * auth.wi-fi.ru → none
     * auth.wi-fi.ru/auth → auth.wi-fi.ru/spb
     * none → auth.wi-fi.ru/spb/gapi/auth/start
     * auth.wi-fi.ru/auth/init → auth.wi-fi.ru/spb/gapi/auth/init
     * auth.wi-fi.ru/auth/check → auth.wi-fi.ru/spb/gapi/auth/check
     * auth.wi-fi.ru/identification → auth.wi-fi.ru/spb/identification
     */
    private Boolean spb = false;

    /**
     * Moscow Metro branch
     * 
     * auth.wi-fi.ru → none
     * auth.wi-fi.ru/auth → auth.wi-fi.ru(/|/new)?
     * none → auth.wi-fi.ru/gapi/auth/start
     * auth.wi-fi.ru/auth/init → auth.wi-fi.ru/gapi/auth/init
     * auth.wi-fi.ru/auth/check → auth.wi-fi.ru/gapi/auth/check
     * auth.wi-fi.ru/identification → auth.wi-fi.ru/identification
     */
    private Boolean mosmetro = false;

    public MosMetroV2(final Context context, final HttpResponse res) {
        super(context);

        /**
         * Checking Internet connection
         * ⇒ GET generate_204 < res
         * ⇐ Meta-redirect: http://auth.wi-fi.ru/?segment=... > redirect, segment
         */
        add(new InitialConnectionCheckTask(this, res) {
            @Override
            public boolean handle_response(HashMap<String, Object> vars, HttpResponse response) {
                try {
                    redirect = response.parseAnyRedirect();
                } catch (ParseException ex) {
                    Logger.log(Logger.LEVEL.DEBUG, ex);
                    Logger.log(Logger.LEVEL.DEBUG, "Redirect not found in response, using default");
                }

                Logger.log(Logger.LEVEL.DEBUG, redirect);

                Uri uri = Uri.parse(redirect);
                String path = uri.getPath();

                if (path.startsWith("/auth")) {
                    vars.put("branch", "default");
                } else if (path.startsWith("/spb")) {
                    vars.put("branch", "spb");
                    spb = true;
                } else if (path.isEmpty() || path.equals("/") || path.startsWith("/new")) {
                    String dn = uri.getQueryParameter("dn");
                    boolean ruckus = dn != null && dn.contains("ruckus");

                    vars.put("branch", ruckus ? "metro-ruckus" : "metro");
                    mosmetro = true;
                } else {
                    vars.put("branch", "unknown");
                    Logger.log(Logger.LEVEL.DEBUG, "Warning: Unknown path" + path);
                }

                Logger.log(Logger.LEVEL.DEBUG, "Branch: " + vars.get("branch"));

                if (uri.getQueryParameter("segment") != null) {
                    vars.put("segment", uri.getQueryParameter("segment"));
                } else {
                    vars.put("segment", "metro");
                }

                Logger.log(Logger.LEVEL.DEBUG, "Segment: " + vars.get("segment"));

                if (uri.getQueryParameter("mac") != null) { // from cppk
                    vars.put("mac", uri.getQueryParameter("mac"));
                }

                if (uri.getQueryParameter("client_mac") != null) { // from metro
                    vars.put("mac", uri.getQueryParameter("client_mac"));
                }

                return true;
            }
        });

        /**
         * Checking for bad redirect
         * redirect ~= welcome.wi-fi.ru
         */
        if (!settings.getBoolean("pref_mosmetro_v3", true))
        add(new Task() {
            @Override
            public boolean run(HashMap<String, Object> vars) {
                if (redirect.contains("welcome.wi-fi.ru")) {
                    Logger.log(Logger.LEVEL.DEBUG, "Found redirect to welcome.wi-fi.ru!");

                    try {
                        HttpResponse response = client.get(redirect).retry().execute();
                        Logger.log(Logger.LEVEL.DEBUG, response.toString());
                    } catch (IOException ex) {
                        Logger.log(Logger.LEVEL.DEBUG, ex);
                    }

                    redirect = Uri.parse(redirect).buildUpon()
                            .authority("auth.wi-fi.ru")
                            .build().toString();

                    vars.put("v3_bypass", "true");
                    Logger.log(Logger.LEVEL.DEBUG, redirect);
                }
                return true;
            }
        });

        /**
         * Getting redirect
         * ⇒ GET http://auth.wi-fi.ru/?segment=... < redirect, segment
         * ⇐ JavaScript Redirect: http://auth.wi-fi.ru/auth?segment=...
         */
        add(new NamedTask(context.getString(R.string.auth_redirect)) {
            @Override
            public boolean run(HashMap<String, Object> vars) {
                try {
                    if (!Patterns.WEB_URL.matcher(redirect).matches()) {
                        throw new ParseException("Invalid URL: " + redirect, 0);
                    }

                    HttpResponse response = client.get(redirect).retry().execute();
                    Logger.log(Logger.LEVEL.DEBUG, response.toString());

                    return true;
                } catch (IOException | ParseException ex) {
                    Logger.log(Logger.LEVEL.DEBUG, ex);
                    Logger.log(context.getString(R.string.error,
                            context.getString(R.string.auth_error_redirect)
                    ));
                    return false;
                }
            }
        });

        /**
         * Async: https://auth.wi-fi.ru/auth
         *        https://auth.wi-fi.ru/metro
         *        https://auth.wi-fi.ru/new
         *        https://auth.wi-fi.ru/spb/new
         * - Parse CSRF token (if present)
         */
        add(new InterceptorTask("https?://auth\\.wi-fi\\.ru/(auth|metro|(spb/)?new)(\\?.*)?") {

            @NonNull @Override
            public HttpResponse response(Client client, HttpRequest request, HttpResponse response) throws IOException {
                try {
                    String csrf_token = response.parseMetaContent("csrf-token");
                    Logger.log(Logger.LEVEL.DEBUG, "CSRF token: " + csrf_token);
                    client.headers.setHeader(Headers.CSRF, csrf_token);
                } catch (ParseException ex) {
                    Logger.log(Logger.LEVEL.DEBUG, "CSRF token not found");
                }

                return response;
            }
        });

        /**
         * Following JavaScript redirect to the auth page
         * redirect = "scheme://host"
         * ⇒ GET http://auth.wi-fi.ru/auth?segment= < redirect + "/auth?segment=" + segment
         * ⇐ Form: method="post" action="/auto_auth" (captcha)
         * ⇐ AJAX: http://auth.wi-fi.ru/auth/init?segment=... (no captcha)
         */
        add(new NamedTask(context.getString(R.string.auth_auth_page)) {
            @Override
            public boolean run(HashMap<String, Object> vars) {
                String url = HttpResponse.removePathFromUrl(redirect);

                if (!spb && !mosmetro) {
                    url += "/auth?segment=" + vars.get("segment");
                } else {
                    if (spb) {
                        url += "/spb";
                    }

                    url += "/gapi/auth/start?segment=" + vars.get("segment");

                    if (vars.containsKey("mac")) {
                        url += "&clientMac=" + vars.get("mac");
                    }
                }

                String prefix = "0:" + random.string(8) + ":";
                client.setCookie("http://auth.wi-fi.ru", "_ym_uid", random.string("0123456789", 19))
                      .setCookie("http://auth.wi-fi.ru", "_mts", prefix + random.string(11) + "~" + random.string(20))
                      .setCookie("http://auth.wi-fi.ru", "_mtp", prefix + random.string(21) + "_" + random.string(10));

                try {
                    HttpResponse response = client.get(url).retry().execute();
                    
                    if (mosmetro || spb) { // expecting JSON
                        Logger.log(Logger.LEVEL.DEBUG, response.toHeaderString());

                        try {
                            JSONObject json = response.json();
                            JSONObject data = (JSONObject) json.get("data");

                            if (data != null) {
                                data.remove("segmentParams");
                                data.remove("userParams");
                            }

                            Logger.log(Logger.LEVEL.DEBUG, json.toJSONString());
                        } catch (org.json.simple.parser.ParseException ex) {
                            Logger.log(Logger.LEVEL.DEBUG, response.toBodyString());
                        }
                    } else {
                        Logger.log(Logger.LEVEL.DEBUG, response.toString());
                    }

                    return true;
                } catch (IOException ex) {
                    Logger.log(Logger.LEVEL.DEBUG, ex);
                    Logger.log(context.getString(R.string.error,
                            context.getString(R.string.auth_error_auth_page)
                    ));
                    return false;
                }
            }
        });

        /**
         * Setting auth token
         * ⇒ GET http://auth.wi-fi.ru/auth/set_token?token= < random.string(6)
         * ⇐ 200 OK
         */
        add(new Task() {
            @Override
            public boolean run(HashMap<String, Object> vars) {
                if (spb || mosmetro) return true;

                String token = new Randomizer(context).string(6);
                Logger.log(Logger.LEVEL.DEBUG, "Trying to set auth token: " + token);

                String url = HttpResponse.removePathFromUrl(redirect);
                url += "/auth/set_token?token=" + token;

                try {
                    HttpResponse response = client.get(url).execute();
                    Logger.log(Logger.LEVEL.DEBUG, response.getPageContent().outerHtml());
                } catch (IOException ex) {
                    Logger.log(Logger.LEVEL.DEBUG, ex);
                }
                return true;
            }
        });

        if (!settings.getBoolean("pref_delay_always", false))
        add(new NamedTask(context.getString(R.string.notification_progress_waiting)) {
            @Override
            public boolean run(HashMap<String, Object> vars) {
                random.delay(running); return true;
            }
        });

        /**
         * Sending login form
         * ⇒ POST http://auth.wi-fi.ru/auth/init?... < redirect, segment, TODO: mode=?
         * ⇒ Cookie: afVideoPassed = 0
         * ⇒ Header: CSRF-Token = ...
         * ⇐ JSON
         */
        add(new NamedTask(context.getString(R.string.auth_auth_form)) {
            @Override
            public boolean run(HashMap<String, Object> vars) {
                String url = HttpResponse.removePathFromUrl(redirect);

                HashMap<String,String> params = new HashMap<>();
                params.put("mode", "0");
                params.put("segment", (String) vars.get("segment"));

                if (!spb && !mosmetro) {
                    url += "/auth/init";
                } else {
                    if (spb) {
                        url += "/spb";
                    }

                    url += "/gapi/auth/init";
                }

                try {
                    HttpResponse res = client.post(url, params).retry().execute();
                    Logger.log(Logger.LEVEL.DEBUG, res.toString());

                    try {
                        JSONObject data = res.json();

                        if (data.containsKey("auth_error_code")) {
                            String error_code = (String) data.get("auth_error_code");

                            if (error_code != null && error_code.startsWith("err_device_not_identified")) {
                                Logger.log(context.getString(R.string.error,
                                        context.getString(R.string.auth_error_not_registered)
                                ));

                                vars.put("result", RESULT.NOT_REGISTERED);
                                return false;
                            }
                        }

                        boolean error = Boolean.FALSE.equals(data.get("result"));
                        error |= "fail".equals(data.get("auth_status"));

                        if (error) {
                            throw new ParseException("Server returned an error", 0);
                        }
                    } catch (org.json.simple.parser.ParseException ex) {
                        Logger.log(Logger.LEVEL.DEBUG, res.toString());
                        Logger.log(Logger.LEVEL.DEBUG, "Unable to parse: response is not JSON");
                    }
                } catch (ProtocolException ignored) { // Too many follow-up requests
                } catch (IOException|ParseException ex) {
                    Logger.log(Logger.LEVEL.DEBUG, ex);
                    Logger.log(context.getString(R.string.error,
                            context.getString(R.string.auth_error_server)
                    ));
                    return false;
                }
                return true;
            }
        });

        /**
         * Checking auth state
         * ⇒ GET http://auth.wi-fi.ru/auth/check?segment=... < redirect, segment
         * ⇐ JSON result == true
         */
        add(new NamedTask(context.getString(R.string.auth_checking_connection)) {
            @Override
            public boolean run(HashMap<String, Object> vars) {
                String url = HttpResponse.removePathFromUrl(redirect);

                if (!spb && !mosmetro) {
                    url += "/auth/check?segment=" + vars.get("segment");
                } else {
                    if (spb) {
                        url += "/spb";
                    }

                    url += "/gapi/auth/check?segment=" + vars.get("segment");
                }

                try {
                    HttpResponse res = client.get(url).retry().execute();
                    Logger.log(Logger.LEVEL.DEBUG, res.toString());
                } catch (IOException ex) {
                    Logger.log(Logger.LEVEL.DEBUG, ex);
                    Logger.log(context.getString(R.string.error,
                            context.getString(R.string.auth_error_server)
                    ));
                    return false;
                }

                return true;
            }
        });

        /**
         * Checking Internet connection
         */
        add(new Task() {
            @Override
            public boolean run(HashMap<String, Object> vars) {
                if (isConnected()) {
                    Logger.log(context.getString(R.string.auth_connected));
                    vars.put("result", RESULT.CONNECTED);
                    return true;
                } else {
                    Logger.log(context.getString(R.string.error,
                            context.getString(R.string.auth_error_connection)
                    ));
                    return false;
                }
            }
        });
    }

    /**
     * Checks if current network is supported by this Provider implementation.
     * @param response  Instance of ParsedResponse.
     * @return          True if response matches this Provider implementation.
     */
    public static boolean match(HttpResponse response) {
        String redirect;

        try {
            redirect = response.parseAnyRedirect();
        } catch (ParseException ex) {
            return false;
        }

        return redirect.contains(".wi-fi.ru") && !redirect.contains("login.wi-fi.ru");
    }
}
