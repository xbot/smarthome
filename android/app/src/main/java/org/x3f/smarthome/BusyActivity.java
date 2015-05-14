package org.x3f.smarthome;

import android.app.Activity;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.SharedPreferences.Editor;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.util.Log;
import android.view.KeyEvent;
import android.view.Window;
import android.widget.Toast;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.koushikdutta.async.http.AsyncHttpClient;
import com.koushikdutta.async.http.AsyncHttpClient.JSONObjectCallback;
import com.koushikdutta.async.http.AsyncHttpClient.WebSocketConnectCallback;
import com.koushikdutta.async.http.AsyncHttpGet;
import com.koushikdutta.async.http.AsyncHttpPost;
import com.koushikdutta.async.http.AsyncHttpResponse;
import com.koushikdutta.async.http.WebSocket;
import com.koushikdutta.async.http.WebSocket.StringCallback;
import com.koushikdutta.async.http.body.JSONObjectBody;

import org.eclipse.paho.client.mqttv3.MqttClient;
import org.eclipse.paho.client.mqttv3.MqttException;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.IOException;
import java.util.HashMap;

public class BusyActivity extends Activity {
	
	public static final String TAG = "BusyActivity";
    public static final String DEVICE_NAME = "SmartHomeClient";
    public static final String SERVER_DEVICE_NAME = "SmartHome";

	private SharedPreferences sharedPref;
	private String apiKey;
	private String deviceIden;
	private String serverDeviceIden;
	private Editor prefEditor;
    private double lastFetch = System.currentTimeMillis() / 1000.0;
    private ObjectMapper objectMapper = new ObjectMapper();
    private long exitTime;
    private MqttClient client;

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
            followCommand();
		}

		AsyncHttpClient.getDefaultInstance().websocket(
			"wss://stream.pushbullet.com/websocket/" + apiKey,
			"wss", new WebSocketConnectCallback() {
				@Override
				public void onCompleted(Exception ex, WebSocket webSocket) {
					if (ex != null) {
						ex.printStackTrace();
						return;
					}
					webSocket.setStringCallback(new StringCallback() {
						@Override
						public void onStringAvailable(
								String rawStr) {
							try {
								HashMap<String, String> data = objectMapper.readValue(rawStr, new TypeReference<HashMap<String, String>>() {});
								if (data.containsKey("type") && data.get("type").equals("tickle")
										&& data.containsKey("subtype") && data.get("subtype").equals("push")) {
									// Fetch pushes from last time
									AsyncHttpGet get = new AsyncHttpGet("https://api.pushbullet.com/v2/pushes?modified_after=" + String.valueOf(lastFetch));
									get.addHeader("Authorization", "Bearer " + apiKey);
									AsyncHttpClient.getDefaultInstance().executeJSONObject(get, new JSONObjectCallback() {

										@Override
										public void onCompleted(Exception arg0,
												AsyncHttpResponse arg1,
												JSONObject responseData) {
											try {
                                                JSONArray pushes = responseData.getJSONArray("pushes");
//                                                Log.d(TAG, "Got pushes: " + pushes.toString());
                                                for (int i=0; i < pushes.length(); i++) {
                                                    JSONObject push = (JSONObject) pushes.get(i);
                                                    if (push.has("target_device_iden") && push.getString("target_device_iden").equals(deviceIden)) {
                                                        Log.d(TAG, "Got response: " + push.toString());
                                                        Intent carrier = new Intent();
                                                        JSONObject data = new JSONObject(push.getString("body"));
                                                        carrier.putExtra("data", data.toString());
                                                        carrier.putExtra("type", push.getString("title"));
                                                        setResult(RESULT_OK, carrier);
                                                        finish();
                                                    }
                                                }
											} catch (JSONException e) {
                                                e.printStackTrace();
												Log.e(TAG, e.getMessage());
                                                Toast.makeText(getApplicationContext(), e.getMessage(), Toast.LENGTH_SHORT).show();
											}
										}

									});
									lastFetch = System.currentTimeMillis() / 1000.0;
								}
							} catch (IOException e) {
                                e.printStackTrace();
                                Log.e(TAG, e.getMessage());
                                Toast.makeText(getApplicationContext(), e.getMessage(), Toast.LENGTH_SHORT).show();
							}
						}
					});
				}
			}
		);

        try {
            client = new MqttClient("tcp://localhost:1883", "pahomqttpublish1");
        } catch (MqttException e) {
            e.printStackTrace();
        }
    }

    private void followCommand() {
        Intent it = getIntent();
		String job = it.getStringExtra("job");
        Log.d(TAG, "Do the job: " + job);
        try {
            AsyncHttpPost post = new AsyncHttpPost("https://api.pushbullet.com/v2/pushes");
            post.addHeader("Authorization", "Bearer " + apiKey);
            JSONObject requestData = new JSONObject();
            requestData.put("device_iden", serverDeviceIden);
            requestData.put("source_device_iden", deviceIden);
            requestData.put("type", "note");
            requestData.put("title", "command");
            requestData.put("body", "{\"command\":\"" + job + "\"}");
            post.setBody(new JSONObjectBody(requestData));
            AsyncHttpClient.getDefaultInstance().executeJSONObject(post, new JSONObjectCallback() {

                @Override
                public void onCompleted(Exception arg0, AsyncHttpResponse arg1,
                        JSONObject responseData) {
                    if (responseData == null) {
                        Log.e(TAG, "Cannot get response data, request failed.");
                        Intent carrier = new Intent();
                        carrier.putExtra("msg", getString(R.string.msg_request_failed));
                        setResult(RESULT_CANCELED, carrier);
                        finish();
                    } else {
                        Log.d(TAG, "Command pushing status: " + responseData.toString());
                    }
                }

            });
        } catch (JSONException e1) {
            e1.printStackTrace();
            Log.e(TAG, e1.getMessage());
            Toast.makeText(getApplicationContext(), e1.getMessage(), Toast.LENGTH_SHORT).show();
        }
    }

    private void findDevices() {
		Log.d(TAG, "No stored device iden found, check if one already exists.");
		AsyncHttpGet get = new AsyncHttpGet("https://api.pushbullet.com/v2/devices");
		get.addHeader("Authorization", "Bearer " + apiKey);
		AsyncHttpClient.getDefaultInstance().executeJSONObject(get, new JSONObjectCallback() {

			@Override
			public void onCompleted(Exception arg0, AsyncHttpResponse arg1,
					JSONObject responseData) {
				try {
					JSONArray devices = responseData.getJSONArray("devices");
					for (int i = 0; i < devices.length(); i++) {
                        JSONObject device = devices.getJSONObject(i);
                        Log.d(TAG, "Found device " + device.toString());
                        if (!device.has("nickname") || !device.has("iden"))
                            continue;
                        if (device.getString("nickname").equals(DEVICE_NAME)) {
                            Log.d(TAG, "Found the right device.");
                            deviceIden = device.getString("iden");
                            prefEditor.putString("device_iden", deviceIden);
                            prefEditor.commit();
                        }
                        if (device.getString("nickname").equals(SERVER_DEVICE_NAME)) {
                            Log.d(TAG, "Found the server device.");
                            serverDeviceIden = device.getString("iden");
                            prefEditor.putString("server_device_iden", serverDeviceIden);
                            prefEditor.commit();
                        }
                        if (deviceIden.length() > 0 && serverDeviceIden.length() > 0) {
                            followCommand();
                            return;
                        }
					}
					
					// Return if deviceIden has been found, if serverDeviceIden has not been found,
					// functions won't work in the future.
					if (deviceIden.length()>0) {
                        followCommand();
                        return;
                    }

					// Create a device if no one exists
					Log.d(TAG, "Cannot find the device, try to create one.");
					try {
			        	AsyncHttpPost post = new AsyncHttpPost("https://api.pushbullet.com/v2/devices");
						post.addHeader("Authorization", "Bearer " + apiKey);
				        JSONObject requestData = new JSONObject();
						requestData.put("nickname", DEVICE_NAME);
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

                                        followCommand();
									} catch (JSONException e) {
                                        e.printStackTrace();
                                        Log.e(TAG, e.getMessage());
                                        Toast.makeText(getApplicationContext(), e.getMessage(), Toast.LENGTH_SHORT).show();
									}
								} else {
                                    Log.e(TAG, "Failed creating device, got response: " + responseData.toString());
                                    Toast.makeText(getApplicationContext(), getString(R.string.msg_device_creation_failure), Toast.LENGTH_SHORT).show();
								}
							}
							
						});
					} catch (JSONException e) {
                        e.printStackTrace();
                        Log.e(TAG, e.getMessage());
                        Toast.makeText(getApplicationContext(), e.getMessage(), Toast.LENGTH_SHORT).show();
					}
				} catch (JSONException e) {
                    e.printStackTrace();
                    Log.e(TAG, e.getMessage());
                    Toast.makeText(getApplicationContext(), e.getMessage(), Toast.LENGTH_SHORT).show();
				}
			}
		});
	}

    @Override
    public boolean onKeyDown(int keyCode, KeyEvent event) {
        if (keyCode == KeyEvent.KEYCODE_BACK
                && event.getAction() == KeyEvent.ACTION_DOWN) {
            if (System.currentTimeMillis() - exitTime > 2000) {
                Toast.makeText(this.getApplicationContext(),
                        this.getString(R.string.msg_cancel_job), Toast.LENGTH_SHORT)
                        .show();
                exitTime = System.currentTimeMillis();
            } else {
                Intent carrier = new Intent();
                carrier.putExtra("msg", getString(R.string.msg_job_canceled));
                setResult(RESULT_CANCELED, carrier);
                finish();
                System.exit(0);
            }
            return true;
        }
        return super.onKeyDown(keyCode, event);
    }
}
