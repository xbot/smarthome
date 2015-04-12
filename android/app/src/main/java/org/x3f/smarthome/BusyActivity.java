package org.x3f.smarthome;

import android.app.Activity;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.SharedPreferences.Editor;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.util.Log;
import android.view.Window;

import com.fasterxml.jackson.core.JsonParseException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonMappingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.koushikdutta.async.http.AsyncHttpClient;
import com.koushikdutta.async.http.AsyncHttpClient.JSONObjectCallback;
import com.koushikdutta.async.http.AsyncHttpGet;
import com.koushikdutta.async.http.AsyncHttpPost;
import com.koushikdutta.async.http.AsyncHttpResponse;
import com.koushikdutta.async.http.WebSocket;
import com.koushikdutta.async.http.body.JSONObjectBody;
import com.koushikdutta.async.http.AsyncHttpClient.WebSocketConnectCallback;
import com.koushikdutta.async.http.WebSocket.StringCallback;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.IOException;
import java.util.HashMap;

public class BusyActivity extends Activity {
	
	public static final String TAG = "SmartHomeBusy";
	private SharedPreferences sharedPref;
	private String apiKey;
	private String deviceIden;
	private String serverDeviceIden;
	private Editor prefEditor;
    private double lastFetch = System.currentTimeMillis() / 1000.0;
    private ObjectMapper objectMapper = new ObjectMapper();

	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		requestWindowFeature(Window.FEATURE_NO_TITLE);
		setContentView(R.layout.activity_busy);
		
		setFinishOnTouchOutside(false);
        Log.e(TAG, "-----------------" + this.toString());
		
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
                                                        Intent carier = new Intent();
                                                        JSONObject data = new JSONObject(push.getString("body"));
                                                        carier.putExtra("data", data.toString());
                                                        Log.e(TAG, "xxx:" + data.toString());
                                                        setResult(RESULT_OK, carier);
                                                        finish();
                                                    }
                                                }
											} catch (JSONException e) {
												// TODO Auto-generated catch block
												e.printStackTrace();
											}
										}

									});
									lastFetch = System.currentTimeMillis() / 1000.0;
								}
							} catch (JsonParseException e) {
								// TODO Auto-generated catch
								// block
								e.printStackTrace();
							} catch (JsonMappingException e) {
								// TODO Auto-generated catch
								// block
								e.printStackTrace();
							} catch (IOException e) {
								// TODO Auto-generated catch
								// block
								e.printStackTrace();
							}
						}
					});
				}
			}
		);
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
                    Log.d(TAG, "Command pushing status: " + responseData.toString());
                }

            });
        } catch (JSONException e1) {
            // TODO Auto-generated catch block
            e1.printStackTrace();
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

                                        followCommand();
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
