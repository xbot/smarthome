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

import sys
import os
import time
import types
import json
from ConfigParser import ConfigParser, NoSectionError, NoOptionError
from pushbullet import Pushbullet, Listener, PushbulletError
from systemd import journal

CONFIG_FILE = '/etc/smarthome.conf'

class Account:

    def __init__(self, apiKey):
        self.api_key = apiKey

class SmartHome:

    lastFetch = None

    def __init__(self, cfg):
        self.cfg = cfg

    def followCommand(self, cmd):
        if cmd['command'] == 'start_motion':
            os.system('systemctl start motion')
        elif cmd['command'] == 'stop_motion':
            os.system('systemctl stop motion')

    def run(self):
        apiKey = self.cfg.get('global', 'api_key')
        pb = Pushbullet(apiKey)

        try:
            deviceIden = self.cfg.get('global', 'device_iden')
        except NoOptionError:
            deviceIden = pb.new_device('SmartHome').device_iden
            self.cfg.set('global', 'device_iden', deviceIden)
            with open(CONFIG_FILE, 'w') as f:
                self.cfg.write(f)

        def on_push(msg):
            try:
                pushes = pb.get_pushes(self.lastFetch)
                self.lastFetch = time.time()
                if type(pushes) is types.TupleType and len(pushes)>1 \
                        and type(pushes[1]) is types.ListType:
                    for msg in pushes[1]:
                        if msg.has_key('target_device_iden') and msg['target_device_iden'] == deviceIden:
                            cmd = json.loads(msg['body'])
                            journal.send('Follow command: ' + msg['body'])
                            self.followCommand(cmd)
            except (PushbulletError, IOError, ValueError, KeyError), e:
                journal.send(e.message, PRIORITY=journal.LOG_ERR)

        lsr = Listener(Account(apiKey), on_push)
        lsr.run()


if __name__ == '__main__':
    try:
        cfg = ConfigParser()
        cfg.read(CONFIG_FILE)
        cfg.get('global', 'api_key')

        sm = SmartHome(cfg)
        sm.run()
    except (NoSectionError, NoOptionError), e:
        err = 'Config file is missing or invalid: ' + str(e)
        journal.send(err, PRIORITY=journal.LOG_ERR)
        sys.stderr.write(err + "\n")
        sys.exit(1)
    except KeyboardInterrupt:
        print('Game over.')
