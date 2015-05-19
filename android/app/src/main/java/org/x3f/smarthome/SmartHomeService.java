package org.x3f.smarthome;

import android.app.Service;
import android.content.Intent;
import android.content.SharedPreferences;
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

public class SmartHomeService extends Service {

    public static final String TAG = "SmartHomeService";
    private SharedPreferences sharedPref;
    private MqttClient client;
    private MyBinder mBinder = new MyBinder();

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
                public void messageArrived(String s, MqttMessage mqttMessage) throws Exception {
                    Log.d(TAG, "Got response: " + mqttMessage.toString());
                    Intent it = new Intent(Constants.BROADCAST_CHANNEL);
                    it.putExtra("data", mqttMessage.toString());
                    sendBroadcast(it);
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
