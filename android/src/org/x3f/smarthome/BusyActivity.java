package org.x3f.smarthome;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import com.koushikdutta.async.http.AsyncHttpClient;
import com.koushikdutta.async.http.AsyncHttpGet;
import com.koushikdutta.async.http.AsyncHttpPost;
import com.koushikdutta.async.http.AsyncHttpResponse;
import com.koushikdutta.async.http.AsyncHttpClient.JSONObjectCallback;
import com.koushikdutta.async.http.body.JSONObjectBody;

import android.app.Activity;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.SharedPreferences.Editor;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.util.Log;
import android.view.Window;

public class BusyActivity extends Activity {
	
	public static final String TAG = "SmartHomeBusy";
	private SharedPreferences sharedPref;
	private String apiKey;
	private String deviceIden;
	private String serverDeviceIden;
	private Editor prefEditor;

	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		requestWindowFeature(Window.FEATURE_NO_TITLE);
		setContentView(R.layout.activity_busy);
		
		setFinishOnTouchOutside(false);
		
		sharedPref = PreferenceManager.getDefaultSharedPreferences(this);
		prefEditor = sharedPref.edit();
		apiKey = sharedPref.getString("api_key", "");
		deviceIden = sharedPref.getString("device_iden", "");
		serverDeviceIden = sharedPref.getString("server_device_iden", "");
		Log.d(TAG, "Stored device iden: " + deviceIden + ", server device iden: " + serverDeviceIden);
		
		// If no device iden is found, check if there is already one with the specified name.
		if (deviceIden.length() == 0 || serverDeviceIden.length() == 0) {
			findDevices();
		} else {
			// TODO do the job
		}
		
//		Intent it = getIntent();
//		String job = it.getStringExtra("job");
//		if (job.equals("get_status")) {
//			try {
//				AsyncHttpPost post = new AsyncHttpPost("https://api.pushbullet.com/v2/pushes");
//				post.addHeader("Authorization", "Bearer " + apiKey);
//		        JSONObject requestData = new JSONObject();
//				requestData.put("device_iden", it.getStringExtra("target_device_iden"));
//				requestData.put("source_device_iden", it.getStringExtra("source_device_iden"));
//				requestData.put("type", "note");
//				requestData.put("title", "command");
//				requestData.put("body", "{\"command\":\"get_status\"}");
//				post.setBody(new JSONObjectBody(requestData));
//				AsyncHttpClient.getDefaultInstance().executeJSONObject(post, new JSONObjectCallback() {
//
//					@Override
//					public void onCompleted(Exception arg0, AsyncHttpResponse arg1,
//							JSONObject responseData) {
//						Log.d(TAG, responseData.toString());
//						// TODO 怎样回传数据给MainActivity
//					}
//					
//				});
//			} catch (JSONException e1) {
//				// TODO Auto-generated catch block
//				e1.printStackTrace();
//			}
//		}
	}

	private void findDevices() {
		Log.d(TAG, "No stored device iden found, check if one already exists.");
		AsyncHttpGet get = new AsyncHttpGet("https://api.pushbullet.com/v2/devices");
		get.addHeader("Authorization", "Bearer " + apiKey);
		AsyncHttpClient.getDefaultInstance().executeJSONObject(get, new JSONObjectCallback() {

			@Override
			public void onCompleted(Exception arg0, AsyncHttpResponse arg1,
					JSONObject response) {
				try {
					JSONArray devices = response.getJSONArray("devices");
					for (int i = 0; i < devices.length(); i++) {
						JSONObject device = devices.getJSONObject(i);
						Log.d(TAG, "Found device " + device.toString());
						if (!device.has("nickname") || !device.has("iden"))
							continue;
						if (device.getString("nickname").equals(MainActivity.deviceName)) {
							Log.d(TAG, "Found the right device.");
							deviceIden = device.getString("iden");
							prefEditor.putString("device_iden", deviceIden);
							prefEditor.commit();
						}
						if (device.getString("nickname").equals(MainActivity.serverDeviceName)) {
							Log.d(TAG, "Found the server device.");
							serverDeviceIden = device.getString("iden");
							prefEditor.putString("server_device_iden", serverDeviceIden);
							prefEditor.commit();
						}
						if (deviceIden.length()>0 && serverDeviceIden.length()>0)
							return;
					}
					
					// Return if deviceIden has been found, if serverDeviceIden has not been found,
					// functions won't work in the future.
					if (deviceIden.length()>0)
						return;
					
					// Create a device if no one exists
					Log.d(TAG, "Cannot find the device, try to create one.");
					try {
			        	AsyncHttpPost post = new AsyncHttpPost("https://api.pushbullet.com/v2/devices");
						post.addHeader("Authorization", "Bearer " + apiKey);
				        JSONObject requestData = new JSONObject();
						requestData.put("nickname", MainActivity.deviceName);
						requestData.put("type", "stream");
						post.setBody(new JSONObjectBody(requestData));
						AsyncHttpClient.getDefaultInstance().executeJSONObject(post, new JSONObjectCallback() {

							@Override
							public void onCompleted(Exception arg0, AsyncHttpResponse arg1,
									JSONObject responseData) {
								if (responseData.has("iden")) {
									try {
										Log.d(TAG, "Device created.");
										deviceIden = responseData.getString("iden");
										prefEditor.putString("device_iden", deviceIden);
										prefEditor.commit();
									} catch (JSONException e) {
										// TODO Auto-generated catch block
										e.printStackTrace();
									}
								} else {
									// TODO 创建设备失败，报错
								}
							}
							
						});
					} catch (JSONException e) {
						// TODO Auto-generated catch block
						e.printStackTrace();
					}
				} catch (JSONException e) {
					// TODO Auto-generated catch block
					e.printStackTrace();
				}
			}
		});
	}
}
