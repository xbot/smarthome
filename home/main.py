#!/usr/bin/env python2
# encoding: utf-8

####################################################
#
# Description:  Alert for invasion using pushbullet.
# Author:       Donie Leigh<donie.leigh at gmail.com>
# License:      MIT
#
####################################################
# TODO do as messages require
# TODO hack get_pushes(), only fetch this device
# TODO start/stop motion
# TODO 
# TODO 

import sys
import time
from pushbullet import Pushbullet, Listener

class Account:

    def __init__(self, apiKey):
        self.api_key = apiKey


if __name__ == '__main__':
    with open('../api_key.dbg', 'r') as f:
        apiKey = f.read().strip()

    pb = Pushbullet(apiKey)

    try:
        with open('../device_iden.dbg', 'r') as f:
            deviceIden = f.read().strip()
    except IOError:
        deviceIden = pb.new_device('Home').device_iden
        with open('../device_iden.dbg', 'w') as f:
            f.write(deviceIden)

    def on_push(msg):
        print pb.get_pushes(time.time()-10)

    lsr = Listener(Account(apiKey), on_push)
    lsr.run()
