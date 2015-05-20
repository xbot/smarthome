package org.x3f.smarthome;

import android.app.Notification;
import android.app.NotificationManager;
import android.app.Service;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.os.Binder;
import android.os.IBinder;
import android.preference.PreferenceManager;
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

public class SmartHomeService extends Service {

    public static final String TAG = "SmartHomeService";
    private SharedPreferences sharedPref;
    private MqttClient client;
    private MyBinder mBinder = new MyBinder();
    private int notificationId = 1;

    public SmartHomeService() {
    }

    @Override
    public void onCreate() {
        super.onCreate();

        sharedPref = PreferenceManager.getDefaultSharedPreferences(this);
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        String host = sharedPref.getString(Constants.OPT_MQ_HOST, "");
        String userName = sharedPref.getString(Constants.OPT_MQ_LOGIN, "");
        String password = sharedPref.getString(Constants.OPT_MQ_PASSWD, "");
        MemoryPersistence persistence = new MemoryPersistence();
        final MqttConnectOptions connOpts = new MqttConnectOptions();
        connOpts.setUserName(userName);
        connOpts.setPassword(password.toCharArray());
        try {
            client = new MqttClient("tcp://" + host + ":1883", Constants.MQ_CLIENTID, persistence);
            client.connect(connOpts);
            client.subscribe(Constants.TOPIC_SERVER, 0);
            client.subscribe(Constants.TOPIC_IMAGE, 0);
            client.setCallback(new MqttCallback() {
                @Override
                public void connectionLost(Throwable throwable) {
                    Log.e(TAG, throwable.getMessage());
                    try {
                        client.connect(connOpts);
                    } catch (MqttException e) {
                        e.printStackTrace();
                        Log.e(TAG, "Reconnecting failed: " + e.getMessage());
                    }
                }

                @Override
                public void messageArrived(String topic, MqttMessage mqttMessage) throws Exception {
                    NotificationManager nm = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
                    Notification.Builder mBuilder = new Notification.Builder(getApplicationContext());
                    mBuilder.setContentTitle(getString(R.string.msg_invasion_alert))
                            .setContentText("")
                            .setSmallIcon(R.drawable.ic_launcher)
                            .setDefaults(Notification.DEFAULT_VIBRATE)
                            .setWhen(System.currentTimeMillis());
                    if (topic.equals(Constants.TOPIC_SERVER)) {
                        Log.d(TAG, "Got response: " + mqttMessage.toString());
                        try {
                            JSONObject msg = new JSONObject(mqttMessage.toString());
                            if (msg.has("type") && msg.getString("type").equals("alert")) {
                                mBuilder.setContentText(msg.getString("data"));
                                nm.notify(notificationId++, mBuilder.build());
                            } else {
                                Intent it = new Intent(Constants.BROADCAST_CHANNEL);
                                it.putExtra("data", mqttMessage.toString());
                                sendBroadcast(it);
                            }
                        } catch (JSONException e) {
                            e.printStackTrace();
                            Log.e(TAG, e.getMessage());
                        }
                    } else if (topic.equals(Constants.TOPIC_IMAGE)) {
                        Log.d(TAG, "Got an image file.");
                        final Notification.BigPictureStyle bigPictureStyle = new Notification.BigPictureStyle();
                        bigPictureStyle.setBigContentTitle(getString(R.string.msg_invasion_alert));
                        byte[] bytes = mqttMessage.getPayload();
                        final Bitmap bitmap = BitmapFactory.decodeByteArray(bytes, 0, bytes.length);
                        bigPictureStyle.bigPicture(bitmap);
                        mBuilder.setStyle(bigPictureStyle);
                        nm.notify(notificationId++, mBuilder.build());
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
        return super.onStartCommand(intent, flags, startId);
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
