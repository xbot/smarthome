#!/usr/bin/env python2
# encoding: utf-8

####################################################
#
# Description:  Alert for invasion using pushbullet.
# Author:       Donie Leigh<donie.leigh at gmail.com>
# License:      MIT
#
####################################################
# TODO hack get_pushes(), only fetch this device
# TODO add configuration validations

import sys
import os
import time
import types
import json
import commands
from ConfigParser import ConfigParser, NoSectionError, NoOptionError
from pushbullet import Pushbullet, Listener, PushbulletError
import paho.mqtt.client as mqtt
from systemd import journal

CONFIG_FILE = '/etc/smarthome.conf'

def singleton(cls):
    """ Set the decorated class singleton. """

    instances = {}

    def _singleton(*args, **kw):
        if cls not in instances:
            instances[cls] = cls(*args, **kw)
        return instances[cls]

    return _singleton

class Account(object):

    def __init__(self, apiKey):
        self.api_key = apiKey

@singleton
class SmartHome(object):
    """Common bussiness logic."""

    def followCommand(self, cmd, fromIden=None):
        '''Do job as required.'''
	journal.send('Follow command: ' + json.dumps(cmd))
        if cmd['command'] == 'get_status':
            pass
        elif cmd['command'] == 'start_motion':
            os.system('systemctl start motion')
        elif cmd['command'] == 'stop_motion':
            os.system('systemctl stop motion')

    def getStatus(self):
        '''Collect the current statuses.'''
        status = {}
        (tmpStatus, tmpOutput) = commands.getstatusoutput('systemctl status motion')
        status['motion'] = tmpStatus == 0 and 'on' or 'off'
        return self.getResponse('status', status)

    def getResponse(self, type, data):
        """Generate response data in JSON format."""
        return json.dumps({'type':type, 'data':data})

class Route(object):
    """Interface of the main bussiness."""

    def __init__(self, cfg):
        self.cfg = cfg
        self.home = SmartHome()

    def run(self):
        '''Keep running and listening for all commands from user.'''
        pass

class PushBulletRoute(Route):
    """SmartHome using pushbullet."""

    def __init__(self, cfg):
        super(PushBulletRoute).__init__(cfg)
        
        self.lastFetch = time.time()
        self.apiKey = self.cfg.get('global', 'api_key')
        self.pb = Pushbullet(self.apiKey)

    def run(self):
        try:
            deviceIden = self.cfg.get('global', 'device_iden')
        except NoOptionError:
            deviceIden = self.pb.new_device('SmartHome').device_iden
            self.cfg.set('global', 'device_iden', deviceIden)
            with open(CONFIG_FILE, 'w') as f:
                self.cfg.write(f)

        def on_push(msg):
            journal.send('Got message: ' + json.dumps(msg))
            try:
                pushes = self.pb.get_pushes(self.lastFetch)
                journal.send('Got pushes: ' + json.dumps(pushes))
                self.lastFetch = time.time()
                if type(pushes) is types.TupleType and len(pushes)>1 \
                        and type(pushes[1]) is types.ListType:
                    for push in pushes[1]:
                        journal.send('Check push: ' + json.dumps(push))
                        if push.has_key('target_device_iden') and push['target_device_iden'] == deviceIden:
                            cmd = json.loads(push['body'])
                            self.home.followCommand(cmd)
                            fromIden = push['source_device_iden']
                            fromDevice = self.getDevice(fromIden)
                            if fromDevice is None:
                                journal.send('get_status: Cannot find device with iden "' + fromIden + '"', PRIORITY=journal.LOG_ERR)
                                return
                            self.pb.push_note('status', self.home.getStatus(), fromDevice)
            except (PushbulletError, IOError, ValueError, KeyError), e:
                journal.send(str(e), PRIORITY=journal.LOG_ERR)

        lsr = Listener(Account(self.apiKey), on_push)
        lsr.run()

    def getDevice(self, iden):
        if type(iden) in [types.StringType, types.UnicodeType]:
            for device in self.pb.devices:
                if device.device_iden == iden:
                    return device
        return None

class MosquittoRoute(Route):
    """SmartHome using mosquitto."""

    def __init__(self, cfg):
        super(self.__class__, self).__init__(cfg)

        self.mq = mqtt.Client()
        self.mq.username_pw_set(self.cfg.get('mosquitto', 'user'), self.cfg.get('mosquitto', 'password'))

        def on_connect(client, userdata, flags, rc):
            journal.send("Connected with result code "+str(rc))
            self.mq.subscribe(self.cfg.get('mosquitto', 'topic_client'))
        self.mq.on_connect = on_connect

        def on_message(client, userdata, msg):
            journal.send('Got message: ' + msg.payload)
            try:
                cmd = json.loads(msg.payload)
            except ValueError, e:
                journal.send('Invalid JSON received: ' + msg.payload + ', ' + e.message, PRIORITY=journal.LOG_ERR)
                return
            self.home.followCommand(cmd)
            self.mq.publish(self.cfg.get('mosquitto', 'topic_msg'), self.home.getStatus(), 2)
        self.mq.on_message = on_message

    def run(self):
        self.mq.connect(self.cfg.get('mosquitto', 'host'), 1883, 60)
        self.mq.loop_forever()


if __name__ == '__main__':
    try:
        cfg = ConfigParser()
        cfg.read(CONFIG_FILE)
        cfg.get('global', 'api_key')

        r = MosquittoRoute(cfg)
        r.run()
    except (NoSectionError, NoOptionError), e:
        err = 'Config file is missing or invalid: ' + str(e)
        journal.send(err, PRIORITY=journal.LOG_ERR)
        sys.stderr.write(err + "\n")
        sys.exit(1)
    except KeyboardInterrupt:
        print('Game over.')
