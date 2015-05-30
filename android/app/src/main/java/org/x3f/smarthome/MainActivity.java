package org.x3f.smarthome;

import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.support.v7.app.ActionBarActivity;
import android.util.Log;
import android.view.KeyEvent;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.View.OnClickListener;
import android.widget.ImageButton;
import android.widget.TextView;
import android.widget.Toast;

import org.json.JSONException;
import org.json.JSONObject;

public class MainActivity extends ActionBarActivity implements OnClickListener {
	
	public static final String TAG = "MainActivity";

	private SharedPreferences sharedPref;
    private boolean initialized = false;
    private JSONObject statuses;
    private long exitTime;

    private ImageButton btnMonitor;
    private TextView textViewTemp;
    private TextView textViewHumidity;

	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.activity_main);
		
		btnMonitor = (ImageButton) findViewById(R.id.btnMonitor);
		btnMonitor.setOnClickListener(this);
        textViewTemp = (TextView) findViewById(R.id.textViewTemp);
        textViewHumidity = (TextView) findViewById(R.id.textViewHumidity);
		
		sharedPref = PreferenceManager.getDefaultSharedPreferences(this);

        // start service
        if (sharedPref.getString(Constants.OPT_PROTOCOL, "").equals(Constants.PROTOCOL_MOSQUITTO)) {
            Intent itService = new Intent(this, SmartHomeService.class);
            startService(itService);
        }
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
        if (id == R.id.action_refresh) {
            Intent it = new Intent(this, BusyActivity.class);
            it.putExtra("job", Constants.CMD_GET_STATUS);
            startActivityForResult(it, Constants.REQUEST_GET_STATUS);
            return true;
        } else if (id == R.id.action_settings) {
			Intent it = new Intent(this, SettingsActivity.class);
			startActivity(it);
			return true;
		} else if (id == R.id.action_about) {
            Intent it = new Intent(this, AboutActivity.class);
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
                if (statuses != null && statuses.has("motion")) {
                    if (statuses.getString("motion").equals("on"))
                        it.putExtra("job", Constants.CMD_STOP_MOTION);
                    else
                        it.putExtra("job", Constants.CMD_START_MOTION);
                }
                startActivityForResult(it, Constants.REQUEST_TOGGLE_MOTION);
            } catch (JSONException e) {
                e.printStackTrace();
                Log.e(TAG, e.getMessage());
                Toast.makeText(getApplicationContext(), e.getMessage(), Toast.LENGTH_SHORT).show();
            }
            break;
        }
	}
	
	public void onResume() {
		super.onResume();
		// Start SettingsActivity if app has not been properly configured
		if (!isConfigured()) {
			Intent it = new Intent(this, SettingsActivity.class);
			startActivity(it);
			return;
		}
		// Start BusyActivity to fetch status
        if (!initialized) {
            initialized = true;
            Intent it = new Intent(this, BusyActivity.class);
            it.putExtra("job", Constants.CMD_GET_STATUS);
            startActivityForResult(it, Constants.REQUEST_GET_STATUS);
        }
	}

    /**
     * Check if the app is properly configured.
     * @return whether the app has been configured.
     */
    private boolean isConfigured() {
        String protocol = sharedPref.getString(Constants.OPT_PROTOCOL, Constants.PROTOCOL_MOSQUITTO);
        if (protocol.equals(Constants.PROTOCOL_PUSHBULLET)) {
            String apiKey = sharedPref.getString(Constants.OPT_PB_APIKEY, "");
            return apiKey.length() > 0;
        } else {
            String login = sharedPref.getString(Constants.OPT_MQ_LOGIN, "");
            String password = sharedPref.getString(Constants.OPT_MQ_PASSWD, "");
            String host = sharedPref.getString(Constants.OPT_MQ_HOST, "");
            return login.length()>0 && password.length()>0 && host.length()>0;
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent carrier) {
//        if (requestCode == REQUEST_GET_STATUS) {
            if (resultCode == RESULT_OK) {
                Log.d(TAG, "Got result: " + carrier.getStringExtra("data"));
                try {
                    if (!carrier.hasExtra("type")) {
                        Log.e(TAG, "Activity result missing the attribute 'type'.");
                        Toast.makeText(getApplicationContext(), String.format(getString(R.string.msg_data_missing), "type"), Toast.LENGTH_SHORT).show();
                        return;
                    }
                    if (!carrier.hasExtra("data")) {
                        Log.e(TAG, "Activity result missing the attribute 'data'.");
                        Toast.makeText(getApplicationContext(), String.format(getString(R.string.msg_data_missing), "data"), Toast.LENGTH_SHORT).show();
                        return;
                    }
                    JSONObject data = new JSONObject(carrier.getStringExtra("data"));
                    if (carrier.getStringExtra("type").equals(Constants.MSG_TYPE_STATUS)
                            && data.has("data")) {
                        statuses = data.getJSONObject("data");
                        // motion
                        if (statuses.has("motion") && statuses.getString("motion").equals("on")) {
                            btnMonitor.setImageDrawable(getResources()
                                    .getDrawable(R.drawable.webcam_on));
                        } else {
                            btnMonitor.setImageDrawable(getResources()
                                    .getDrawable(R.drawable.webcam_off));
                        }
                        // temperature
                        if (statuses.has("temperature")) {
                            textViewTemp.setText(String.format(getString(R.string.tmpl_temperature), statuses.getString("temperature")));
                        }
                        // humidity
                        if (statuses.has("humidity")) {
                            textViewHumidity.setText(String.format(getString(R.string.tmpl_humidity), statuses.getString("humidity")));
                        }
                    }
                } catch (JSONException e) {
                    e.printStackTrace();
                    Log.e(TAG, e.getMessage());
                    Toast.makeText(getApplicationContext(), e.getMessage(), Toast.LENGTH_SHORT).show();
                }
            } else if (resultCode == RESULT_CANCELED) {
                if (carrier.hasExtra("msg")) {
                    Toast.makeText(getApplicationContext(), carrier.getStringExtra("msg"), Toast.LENGTH_SHORT).show();
                }
                if (!initialized)
                    initialized = true;
            }
//        }
    }

    @Override
    public boolean onKeyDown(int keyCode, KeyEvent event) {
        if (keyCode == KeyEvent.KEYCODE_BACK
                && event.getAction() == KeyEvent.ACTION_DOWN) {
            if (System.currentTimeMillis() - exitTime > 2000) {
                Toast.makeText(this.getApplicationContext(),
                        this.getString(R.string.msg_quit), Toast.LENGTH_SHORT)
                        .show();
                exitTime = System.currentTimeMillis();
            } else {
                finish();
                System.exit(0);
            }
            return true;
        }
        return super.onKeyDown(keyCode, event);
    }

}