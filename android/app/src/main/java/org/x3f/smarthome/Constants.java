package org.x3f.smarthome;

/**
 * Created by taoqi on 2015/5/15.
 */
public class Constants {
    // Communication protocol
    public static final String PROTOCOL_MOSQUITTO = "mosquitto";     // Mosquitto
    public static final String PROTOCOL_PUSHBULLET = "pushbullet";    // PushBullet

    // Mosquitto topics
    public static final String TOPIC_CLIENT = "client";
    public static final String TOPIC_SERVER = "server";

    public static final String MQ_CLIENTID = "android";

    // Option names
    public static final String OPT_PROTOCOL = "communication_protocol";
    public static final String OPT_PB_APIKEY = "pb_api_key";
    public static final String OPT_PB_CLIENT_IDEN = "client_iden";
    public static final String OPT_PB_SERVER_IDEN = "server_iden";
    public static final String OPT_MQ_LOGIN = "mq_login";
    public static final String OPT_MQ_PASSWD = "mq_password";
    public static final String OPT_MQ_HOST = "mq_host";

    // Commands
    public static final String CMD_GET_STATUS = "get_status";
    public static final String CMD_START_MOTION = "start_motion";
    public static final String CMD_STOP_MOTION = "stop_motion";

    public static final int REQUEST_GET_STATUS = 1;
    public static final int REQUEST_TOGGLE_MOTION = 2;

    // Intent filter name
    public static final String BROADCAST_CHANNEL = "org.x3f.smarthome.BROARDCAST";
}
