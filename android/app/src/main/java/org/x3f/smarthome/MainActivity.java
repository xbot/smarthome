package org.x3f.smarthome;

import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.support.v7.app.ActionBarActivity;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.View.OnClickListener;
import android.widget.Button;

import org.json.JSONException;
import org.json.JSONObject;

public class MainActivity extends ActionBarActivity implements OnClickListener {
	
	public static final String TAG = "SmartHome";
    public static final String deviceName = "SmartHomeClient";
    public static final String serverDeviceName = "SmartHome";
    public static final int REQUEST_GET_STATUS = 1;
    public static final int REQUEST_TOGGLE_MOTION = 2;

	private SharedPreferences sharedPref;
	private String apiKey;
    private boolean initialized = false;
    private JSONObject statuses;

    private Button btnMonitor;

	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.activity_main);
		
		btnMonitor = (Button) findViewById(R.id.btnMonitor);
		btnMonitor.setOnClickListener(this);
		
		sharedPref = PreferenceManager.getDefaultSharedPreferences(this);
	}

	@Override
	public boolean onCreateOptionsMenu(Menu menu) {
		// Inflate the menu; this adds items to the action bar if it is present.
		getMenuInflater().inflate(R.menu.main, menu);
		return true;
	}

	@Override
	public boolean onOptionsItemSelected(MenuItem item) {
		// Handle action bar item clicks here. The action bar will
		// automatically handle clicks on the Home/Up button, so long
		// as you specify a parent activity in AndroidManifest.xml.
		int id = item.getItemId();
		if (id == R.id.action_settings) {
			Intent it = new Intent(this, SettingsActivity.class);
			startActivity(it);
			return true;
		}
		return super.onOptionsItemSelected(item);
	}

	@Override
	public void onClick(View v) {
		switch (v.getId()) {
		case R.id.btnMonitor:
            try {
                Intent it = new Intent(this, BusyActivity.class);
                if (statuses instanceof JSONObject && statuses.has("motion")) {
                    if (statuses.getString("motion").equals("on"))
                        it.putExtra("job", "stop_motion");
                    else
                        it.putExtra("job", "start_motion");
                }
                startActivityForResult(it, REQUEST_TOGGLE_MOTION);
            } catch (JSONException e) {
                e.printStackTrace();
            }
            return;
        }
	}
	
	public void onResume() {
		super.onResume();
		// Start SettingsActivity if no api key is found
		apiKey = sharedPref.getString("api_key", "");
		Log.d(TAG, "API Key" + apiKey);
		if (apiKey.length() == 0) {
			Intent it = new Intent(this, SettingsActivity.class);
			startActivity(it);
			return;
		}
		// Start BusyActivity to fetch status
        if (!initialized) {
            initialized = true;
            Intent it = new Intent(this, BusyActivity.class);
            it.putExtra("job", "get_status");
            startActivityForResult(it, REQUEST_GET_STATUS);
        }
	}

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent carier) {
//        if (requestCode == REQUEST_GET_STATUS) {
            if (resultCode == RESULT_OK) {
                Log.d(TAG, "Got result: " + carier.getStringExtra("data"));
                try {
                    JSONObject data = new JSONObject(carier.getStringExtra("data"));
                    if (data.has("command") && data.getString("command").equals("get_status")
                            && data.has("data")) {
                        statuses = data.getJSONObject("data");
                        Log.e(TAG, "statuses: " + statuses.toString());
                        if (statuses.has("motion") && statuses.getString("motion").equals("on")) {
                            btnMonitor.setText(getString(R.string.btn_monitor_off));
                        } else {
                            btnMonitor.setText(getString(R.string.btn_monitor_on));
                        }
                    }
                } catch (JSONException e) {
                    e.printStackTrace();
                }
//            }
        }
    }
}