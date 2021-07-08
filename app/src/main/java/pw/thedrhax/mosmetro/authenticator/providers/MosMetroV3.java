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

import android.annotation.SuppressLint;
import android.content.Context;
import android.content.SharedPreferences;
import android.net.Uri;

import org.json.simple.JSONObject;

import java.io.IOException;
import java.text.ParseException;
import java.util.HashMap;
import java.util.Map;

import pw.thedrhax.mosmetro.R;
import pw.thedrhax.mosmetro.authenticator.InitialConnectionCheckTask;
import pw.thedrhax.mosmetro.authenticator.NamedTask;
import pw.thedrhax.mosmetro.authenticator.Provider;
import pw.thedrhax.mosmetro.httpclient.HttpResponse;
import pw.thedrhax.util.Logger;

/**
 * The MosMetroV3 class implements support for welcome.wi-fi.ru algorithm.
 *
 * Detection: Meta or Location redirect contains "welcome.wi-fi.ru".
 *
 * @author Dmitry Karikh <the.dr.hax@gmail.com>
 * @see Provider
 */

public class MosMetroV3 extends Provider {
    private String redirect = "http://welcome.wi-fi.ru/?client_mac=00-00-00-00-00-00";

    public MosMetroV3(final Context context, final HttpResponse res) {
        super(context);

        /**
         * Checking Internet connection
         * ⇒ GET generate_204 < res
         * ⇐ Meta + Location redirect: http://welcome.wi-fi.ru/?client_mac=... > redirect, mac
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

                if (redirect.contains("client_mac")) {
                    vars.put("mac", Uri.parse(redirect).getQueryParameter("client_mac"));
                } else {
                    vars.put("mac", "00-00-00-00-00-00");
                }

                redirect = HttpResponse.removePathFromUrl(redirect);

                return true;
            }
        });

        /**
         * Getting auth page
         * ⇒ GET redirect + /?client_mac=mac < redirect, mac
         * ⇐ 200 OK
         * ⇐ Meta csrf-token > token
         */
        add(new NamedTask(context.getString(R.string.auth_auth_page)) {
            @Override
            public boolean run(HashMap<String, Object> vars) {
                HttpResponse response;

                try {
                    Map<String,String> params = new HashMap<>();
                    params.put("client_mac", (String)vars.get("mac"));
                    response = client.get(redirect, params).retry().execute();
                    Logger.log(Logger.LEVEL.DEBUG, response.getPageContent().outerHtml());
                } catch (IOException ex) {
                    Logger.log(Logger.LEVEL.DEBUG, ex);
                    Logger.log(context.getString(R.string.error,
                            context.getString(R.string.auth_error_auth_page)
                    ));
                    return false;
                }

                try {
                    vars.put("token", response.parseMetaContent("csrf-token"));
                } catch (ParseException ex) {
                    Logger.log(Logger.LEVEL.DEBUG, ex);
                    Logger.log(context.getString(R.string.error,
                            "CSRF token not found"
                    ));
                    return false;
                }

                return true;
            }
        });

        /**
         * Initializing auth procedure
         * ⇒ POST redirect + /auth/init < redirect
         * ⇒ JSON: { "authenticity_token": token, "client_mac": mac, "client_ip": "" } < token, mac
         * ⇐ JSON: { "result": true, "user_mac": ..., "auth_status": "initial" }
         */
        add(new NamedTask(context.getString(R.string.auth_init)) {
            @Override @SuppressLint("HardwareIds")
            public boolean run(HashMap<String, Object> vars) {
                HttpResponse response;

                try {
                    JSONObject body = new JSONObject();
                    body.put("authenticity_token", vars.get("token"));
                    body.put("client_mac", vars.get("mac"));
                    body.put("client_ip", "");

                    response = client.post(
                            redirect + "/auth/init",
                            body.toJSONString(),
                            "application/json; charset=UTF-8"
                    ).retry().execute();

                    Logger.log(Logger.LEVEL.DEBUG, response.toString());
                } catch (IOException ex) {
                    Logger.log(Logger.LEVEL.DEBUG, ex);
                    Logger.log(context.getString(R.string.error,
                            context.getString(R.string.auth_error_server)
                    ));
                    return false;
                }

                try {
                    JSONObject answer = response.json();
                    boolean result = answer.containsKey("result") && answer.get("result").equals(true);
                    if (!result) {
                        throw new Exception("Unexpected answer: false");
                    }
                } catch (Exception ex) {
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
         * Checking auth status
         * ⇒ GET redirect + /auth/check?client_mac=mac&client_ip= < redirect, mac
         */
        add(new NamedTask(context.getString(R.string.auth_check)) {
            @Override
            public boolean run(HashMap<String, Object> vars) {
                try {
                    Map<String,String> params = new HashMap<>();
                    params.put("client_mac", (String)vars.get("mac"));
                    params.put("client_ip", "");

                    HttpResponse response = client.get(redirect + "/auth/check", params).retry().execute();
                    Logger.log(Logger.LEVEL.DEBUG, response.toString());
                } catch (IOException ex) {
                    Logger.log(Logger.LEVEL.DEBUG, ex);
                }
                return true;
            }
        });

        /**
         * Finishing auth procedure
         * ⇒ GET redirect + /success?client_mac=mac < redirect, mac
         * ⇐ Location redirect > client.response()
         */
        add(new NamedTask(context.getString(R.string.auth_finish)) {
            @Override
            public boolean run(HashMap<String, Object> vars) {
                try {
                    Map<String,String> params = new HashMap<>();
                    params.put("client_mac", (String)vars.get("mac"));

                    HttpResponse response = client.get(redirect + "/success", params).retry().execute();
                    vars.put("response", response);
                    Logger.log(Logger.LEVEL.DEBUG, response.toString());
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
         * < client.response()
         * ⇐ GOOD: 204 No Content
         * ⇐ BAD: Meta + Location redirect: http://welcome.wi-fi.ru/?client_mac=... > redirect, mac
         * ⇐ OKAY: Meta + Location redirect: http://auth.wi-fi.ru/?segment=... > redirect
         * ⇐ WTF: https://gowifi.ru
         */
        add(new NamedTask(context.getString(R.string.auth_checking_connection)) {
            @Override
            public boolean run(HashMap<String, Object> vars) {
                Provider provider = Provider.find(context, (HttpResponse)vars.get("response"));

                if (provider instanceof Unknown && isConnected()) {
                    Logger.log(context.getString(R.string.auth_connected));
                    vars.put("result", RESULT.CONNECTED);
                } else if (provider instanceof MosMetroV3) {
                    Logger.log(context.getString(R.string.error,
                            context.getString(R.string.auth_error_loop)
                    ));
                } else {
                    if (provider instanceof Unknown) {
                        Logger.log(context.getString(R.string.auth_unknown_redirect));
                        provider = Provider.find(context, running);
                    }

                    add(indexOf(this) + 1, provider);
                    return true;
                }

                return false;
            }
        });
    }

    /**
     * Checks if current network is supported by this Provider implementation.
     * @param response  Instance of ParsedResponse.
     * @return          True if response matches this Provider implementation.
     */
    public static boolean match(HttpResponse response, SharedPreferences settings) {
        if (!settings.getBoolean("pref_mosmetro_v3", true)) return false;

        String redirect;
        try {
            redirect = response.parseAnyRedirect();
        } catch (ParseException ex) {
            return false;
        }

        return redirect.contains("welcome.wi-fi.ru");
    }
}
