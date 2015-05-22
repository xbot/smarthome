package org.x3f.smarthome;

import android.app.Notification;
import android.app.NotificationManager;
import android.app.Service;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.os.Binder;
import android.os.IBinder;
import android.preference.PreferenceManager;
import android.util.Base64;
import android.util.Log;

import org.eclipse.paho.client.mqttv3.IMqttDeliveryToken;
import org.eclipse.paho.client.mqttv3.MqttCallback;
import org.eclipse.paho.client.mqttv3.MqttClient;
import org.eclipse.paho.client.mqttv3.MqttConnectOptions;
import org.eclipse.paho.client.mqttv3.MqttException;
import org.eclipse.paho.client.mqttv3.MqttMessage;
import org.eclipse.paho.client.mqttv3.persist.MemoryPersistence;
import org.json.JSONException;
import org.json.JSONObject;
import org.x3f.android.Lib;

public class SmartHomeService extends Service {

    public static final String TAG = "SmartHomeService";
    private SharedPreferences sharedPref;
    private MqttClient client;
    private MyBinder mBinder = new MyBinder();
    private int notificationId = 1;
    private BroadcastReceiver connectionReceiver;
    private MqttConnectOptions connOpts;

    public SmartHomeService() {
    }

    @Override
    public void onCreate() {
        super.onCreate();

        sharedPref = PreferenceManager.getDefaultSharedPreferences(this);
        String host = sharedPref.getString(Constants.OPT_MQ_HOST, "");
        String userName = sharedPref.getString(Constants.OPT_MQ_LOGIN, "");
        String password = sharedPref.getString(Constants.OPT_MQ_PASSWD, "");
        connOpts = new MqttConnectOptions();
        connOpts.setUserName(userName);
        connOpts.setPassword(password.toCharArray());
        connOpts.setCleanSession(false);
        MemoryPersistence persistence = new MemoryPersistence();
        try {
            client = new MqttClient("tcp://" + host + ":1883", Constants.MQ_CLIENTID, persistence);
        } catch (MqttException e) {
            e.printStackTrace();
            Log.e(TAG, "Failed to initialize MqttClient.");
        }

        connectionReceiver = new BroadcastReceiver() {

            @Override
            public void onReceive(Context context, Intent intent) {
                ConnectivityManager connectMgr = (ConnectivityManager) getSystemService(CONNECTIVITY_SERVICE);
                NetworkInfo mobNetInfo = connectMgr.getNetworkInfo(ConnectivityManager.TYPE_MOBILE);
                NetworkInfo wifiNetInfo = connectMgr.getNetworkInfo(ConnectivityManager.TYPE_WIFI);

                if (mobNetInfo.isConnected() || wifiNetInfo.isConnected()) {
                    if (client != null && !client.isConnected()) {
                        Log.i(TAG, "Network connected, reconnect mosquitto.");
                        connectService();
                    }
                }
            }

        };

        IntentFilter intentFilter = new IntentFilter();
        intentFilter.addAction(ConnectivityManager.CONNECTIVITY_ACTION);
        registerReceiver(connectionReceiver, intentFilter);
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        connectService();
        return super.onStartCommand(intent, flags, startId);
    }

    @Override
    public void onDestroy() {
        unregisterReceiver(connectionReceiver);
        super.onDestroy();
    }

    private void connectService() {
        try {
            client.connect(connOpts);
            client.subscribe(Constants.TOPIC_SERVER, Constants.QOS_TWO);
            client.subscribe(Constants.TOPIC_IMAGE, Constants.QOS_TWO);
            client.setCallback(new MqttCallback() {
                @Override
                public void connectionLost(Throwable throwable) {
                    Log.e(TAG, throwable.getMessage());
                    Thread t = new Thread(new Runnable() {
                        @Override
                        public void run() {
                            while (Lib.isNetworkConnected(getApplicationContext())) {
                                try {
                                    Log.i(TAG, "Connection lost, try to reconnect mosquitto.");
                                    client.connect(connOpts);
                                    if (!client.isConnected()) {
                                        Thread.sleep(3000);
                                    } else {
                                        break;
                                    }
                                } catch (MqttException e) {
                                    e.printStackTrace();
                                    Log.e(TAG, "Reconnecting failed: " + e.getMessage());
                                } catch (InterruptedException e) {
                                    e.printStackTrace();
                                    Log.e(TAG, e.getMessage());
                                }
                            }
                        }
                    });
                    t.start();
                }

                @Override
                public void messageArrived(String topic, MqttMessage mqttMessage) throws Exception {
                    Log.i(TAG, "Got a message on topic [" + topic + "]: " + mqttMessage.toString());

                    // Create notification builder
                    NotificationManager nm = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
                    Notification.Builder mBuilder = new Notification.Builder(getApplicationContext());
                    mBuilder.setContentTitle(getString(R.string.msg_invasion_alert))
                            .setContentText("")
                            .setSmallIcon(R.drawable.ic_launcher)
                            .setDefaults(Notification.DEFAULT_VIBRATE)
                            .setWhen(System.currentTimeMillis());

                    JSONObject msg = null;
                    try {
                        msg = new JSONObject(mqttMessage.toString());
                    } catch (JSONException e) {
                        e.printStackTrace();
                        Log.e(TAG, e.getMessage());
                        return;
                    }

                    // Handle plain messages
                    if (topic.equals(Constants.TOPIC_SERVER)) {
                        if (msg.has("type") && msg.getString("type").equals(Constants.MSG_TYPE_ALERT)) {
                            mBuilder.setContentText(msg.getString("data"));
                            nm.notify(notificationId++, mBuilder.build());
                        } else {
                            Intent it = new Intent(Constants.BROADCAST_CHANNEL);
                            it.putExtra("data", mqttMessage.toString());
                            sendBroadcast(it);
                        }
                    } else if (topic.equals(Constants.TOPIC_IMAGE)) {
                        // Handle images
                        if (msg.has("image") && msg.has("time")) {
                            byte[] bytes = Base64.decode(msg.getString("image"), Base64.DEFAULT);
                            final Notification.BigPictureStyle bigPictureStyle = new Notification.BigPictureStyle();
                            bigPictureStyle.setBigContentTitle(getString(R.string.msg_invasion_alert));
                            final Bitmap bitmap = BitmapFactory.decodeByteArray(bytes, 0, bytes.length);
                            bigPictureStyle.bigPicture(bitmap);
                            mBuilder.setStyle(bigPictureStyle).setWhen(msg.getLong("time"));
                            nm.notify(notificationId++, mBuilder.build());
                        } else {
                            Log.e(TAG, "Invalid message.");
                        }
                    } else {
                        Log.e(TAG, "Unknown topic: " + topic);
                    }
                }

                @Override
                public void deliveryComplete(IMqttDeliveryToken iMqttDeliveryToken) {

                }
            });
        } catch (MqttException e) {
            e.printStackTrace();
            Log.e(TAG, e.getMessage());
        }
    }

    @Override
    public IBinder onBind(Intent intent) {
        return mBinder;
    }

    class MyBinder extends Binder {

        public void doJob(String job) {
            String payLoad = "{\"command\":\"" + job + "\"}";
            MqttMessage message = new MqttMessage();
            message.setPayload(payLoad.getBytes());
            try {
                client.publish(Constants.TOPIC_CLIENT, message);
            } catch (MqttException e) {
                e.printStackTrace();
                Log.e(TAG, e.getMessage());
            }
        }

    }
}
