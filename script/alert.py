#!/usr/bin/env python2
# encoding: utf-8

####################################################
#
# Description:  Alert for invasion using pushbullet.
# Author:       Donie Leigh<donie.leigh at gmail.com>
# License:      MIT
#
####################################################

import sys, getopt, time, json
from ConfigParser import ConfigParser, NoSectionError, NoOptionError
from systemd import journal

CONFIG_FILE = '/etc/smarthome.conf'

cfg = None
host = None
username = None
password = None
topic_msg = None
topic_image = None
timePoint = time.strftime("%Y-%m-%d %H:%M:%S", time.localtime())
alertMsg = timePoint + '，发现入侵者！！！'
apiKey = None

def send_alert_by_pushbullet(image):
    pb = PushBullet(apiKey)
    pb.push_note('入侵警报', alertMsg)
    pb.push_file(open(image, 'rb'))

def send_alert_by_mosquitto(image):
    mq = mqtt.Client()
    mq.username_pw_set(username, password)
    mq.on_publish = lambda mosq, userdata, mid: mosq.disconnect()
    mq.connect(host, 1883, 60)
    # data = {'type':'alert', 'data':alertMsg}
    # mq.publish(topic_msg, json.dumps(data), 0)
    with open(imageFile, 'rb') as f:
        mq.publish(topic_image, bytearray(f.read()), 2)
    mq.loop_forever()


if __name__ == '__main__':
    opts, args = getopt.getopt(sys.argv[1:], "f:")
    imageFile = ''
    for op, value in opts:
        if op == "-f":
            imageFile = value

    if len(imageFile) == 0:
        sys.stderr.write("Image file missing.\n")
        sys.exit()

    try:
        cfg = ConfigParser()
        cfg.read(CONFIG_FILE)
        protocol = cfg.get('global', 'protocol')
        if protocol == 'pushbullet':
            from yapbl import PushBullet

            apiKey = cfg.get('global', 'apiKey')
            if apiKey is None or len(apiKey) == 0:
                raise NoOptionError('apiKey', 'global')

            send_alert_by_pushbullet(imageFile)
        else:
            import paho.mqtt.client as mqtt

            username = cfg.get('mosquitto', 'user')
            if username is None or len(username) == 0:
                raise NoOptionError('user', 'mosquitto')
            password = cfg.get('mosquitto', 'password')
            if password is None or len(password) == 0:
                raise NoOptionError('password', 'mosquitto')
            host = cfg.get('mosquitto', 'host')
            if host is None or len(host) == 0:
                raise NoOptionError('host', 'mosquitto')
            topic_msg = cfg.get('mosquitto', 'topic_msg')
            if topic_msg is None or len(topic_msg) == 0:
                raise NoOptionError('topic_msg', 'mosquitto')
            topic_image = cfg.get('mosquitto', 'topic_image')
            if topic_image is None or len(topic_image) == 0:
                raise NoOptionError('topic_image', 'mosquitto')

            send_alert_by_mosquitto(imageFile)
    except (NoSectionError, NoOptionError), e:
        err = 'Config file is missing or invalid: ' + str(e)
        journal.send(err, PRIORITY=journal.LOG_ERR)
        sys.stderr.write(err + "\n")
        sys.exit(1)
