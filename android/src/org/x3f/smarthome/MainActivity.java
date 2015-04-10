package org.x3f.smarthome;

import java.io.IOException;
import java.util.HashMap;

import org.json.JSONException;
import org.json.JSONObject;

import com.fasterxml.jackson.core.JsonParseException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonMappingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.koushikdutta.async.http.AsyncHttpClient;
import com.koushikdutta.async.http.AsyncHttpClient.JSONObjectCallback;
import com.koushikdutta.async.http.AsyncHttpClient.WebSocketConnectCallback;
import com.koushikdutta.async.http.AsyncHttpGet;
import com.koushikdutta.async.http.AsyncHttpResponse;
import com.koushikdutta.async.http.WebSocket;
import com.koushikdutta.async.http.WebSocket.StringCallback;
import android.support.v7.app.ActionBarActivity;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.SharedPreferences.Editor;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.View.OnClickListener;
import android.widget.Button;

public class MainActivity extends ActionBarActivity implements OnClickListener {
	
	public static final String TAG = "SmartHome";
	private ObjectMapper om = new ObjectMapper();
	private double lastFetch = System.currentTimeMillis() / 1000.0;
	private SharedPreferences sharedPref;
	private Editor prefEditor;
	private String apiKey;
	private String deviceIden;
	private String serverDeviceIden;
	public static final String deviceName = "SmartHomeClient";
	public static final String serverDeviceName = "SmartHome";
	private Button btnMonitor;

	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.activity_main);
		
		btnMonitor = (Button) findViewById(R.id.btnMonitor);
		btnMonitor.setOnClickListener(this);
		
		sharedPref = PreferenceManager.getDefaultSharedPreferences(this);
		prefEditor = sharedPref.edit();
		
//		deviceIden = sharedPref.getString("device_iden", "");
//		serverDeviceIden = sharedPref.getString("server_device_iden", "");
//		Log.d(TAG, "Stored device iden: " + deviceIden + ", server device iden: " + serverDeviceIden);
//		// If no device iden is found, check if there is already one with the specified name.
//		if (deviceIden.length() == 0 || serverDeviceIden.length() == 0) {
//			
//		}
		
//		AsyncHttpClient.getDefaultInstance().websocket(
//			"wss://stream.pushbullet.com/websocket/" + apiKey,
//			"wss", new WebSocketConnectCallback() {
//				@Override
//				public void onCompleted(Exception ex, WebSocket webSocket) {
//					if (ex != null) {
//						ex.printStackTrace();
//						return;
//					}
//					webSocket.setStringCallback(new StringCallback() {
//						@Override
//						public void onStringAvailable(
//								String rawStr) {
//							try {
//								HashMap<String, String> data = om.readValue(rawStr, new TypeReference<HashMap<String, String>>() {});
//								if (data.containsKey("type") && data.get("type").equals("tickle")
//										&& data.containsKey("subtype") && data.get("subtype").equals("push")) {
//									// Fetch pushes from last time
//									AsyncHttpGet get = new AsyncHttpGet("https://api.pushbullet.com/v2/pushes?modified_after=" + String.valueOf(lastFetch));
//									get.addHeader("Authorization", "Bearer " + apiKey);
//									AsyncHttpClient.getDefaultInstance().executeJSONObject(get, new JSONObjectCallback() {
//
//										@Override
//										public void onCompleted(Exception arg0,
//												AsyncHttpResponse arg1,
//												JSONObject arg2) {
//											// TODO Auto-generated method stub
//											try {
//												Log.e(TAG, arg2.getJSONArray("pushes").toString());
//												// TODO 判断target_device_iden
//											} catch (JSONException e) {
//												// TODO Auto-generated catch block
//												e.printStackTrace();
//											}
//										}
//										
//									});
//									lastFetch = System.currentTimeMillis() / 1000.0;
//								}
//							} catch (JsonParseException e) {
//								// TODO Auto-generated catch
//								// block
//								e.printStackTrace();
//							} catch (JsonMappingException e) {
//								// TODO Auto-generated catch
//								// block
//								e.printStackTrace();
//							} catch (IOException e) {
//								// TODO Auto-generated catch
//								// block
//								e.printStackTrace();
//							}
//						}
//					});
//				}
//			}
//		);
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
//			if (serverDeviceIden.length() < 0) {
//				Toast.makeText(getApplicationContext(), "No server device IDEN is found.", Toast.LENGTH_SHORT).show();
//				return;
//			}
//			Intent it = new Intent(this, BusyActivity.class);
//			it.putExtra("job", "get_status");
//			it.putExtra("target_device_iden", serverDeviceIden);
//			it.putExtra("source_device_iden", deviceIden);
//			startActivity(it);
			return;
		}
	}
	
	public void onResume() {
		super.onResume();
		// Start SettingsActivity if no api key is found
		apiKey = sharedPref.getString("api_key", "");
		Log.e(TAG, "API Key" + apiKey);
		if (apiKey.length() == 0) {
			Intent it = new Intent(this, SettingsActivity.class);
			startActivity(it);
			return;
		}
		// TODO start busyactivity to fetch status
	}
}
