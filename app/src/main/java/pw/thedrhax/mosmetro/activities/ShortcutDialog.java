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

package pw.thedrhax.mosmetro.activities;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.CheckBox;

import pw.thedrhax.mosmetro.R;

public class ShortcutDialog extends Activity {
    private CheckBox check_background;
    private CheckBox check_force;

    @Override
    protected void onCreate(final Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.shortcut_activity);

        check_background = (CheckBox)findViewById(R.id.check_background);
        check_force = (CheckBox)findViewById(R.id.check_force);
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        // Show back button in menu
        try {
            getActionBar().setDisplayHomeAsUpEnabled(true);
        } catch (NullPointerException ignored) {}

        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
            case android.R.id.home:
                finish();
                return true;
        }
        return super.onOptionsItemSelected(item);
    }

    public void check_background(View view) {
        check_force.setEnabled(check_background.isChecked());
    }

    public void button_save(View view) {
        Intent result = new Intent();

        boolean background = check_background.isChecked();

        Intent shortcut_intent = new Intent(
                this, background ? ConnectionServiceActivity.class : DebugActivity.class
        ).putExtra("force", check_force.isChecked());

        result.putExtra(Intent.EXTRA_SHORTCUT_INTENT, shortcut_intent);
        result.putExtra(
                Intent.EXTRA_SHORTCUT_NAME,
                background ? getString(R.string.in_background) : getString(R.string.connect)
        );
        result.putExtra(
                Intent.EXTRA_SHORTCUT_ICON_RESOURCE,
                Intent.ShortcutIconResource.fromContext(this, R.mipmap.ic_launcher)
        );

        if ("android.intent.action.CREATE_SHORTCUT".equals(getIntent().getAction())) {
            setResult(RESULT_OK, result);
        } else {
            result.setAction("com.android.launcher.action.INSTALL_SHORTCUT");
            sendBroadcast(result);
        }

        finish();
    }

    public void button_cancel (View view) {
        finish();
    }
}
