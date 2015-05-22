#!/bin/bash

CONFIG_FILE=/etc/smarthome.conf

function read_option()
{
python2 <<PARSEINI
from ConfigParser import ConfigParser, NoSectionError, NoOptionError
try:
    cfg = ConfigParser()
    cfg.read('$CONFIG_FILE')
    protocol = cfg.get('$1', '$2')
    print protocol
except (NoSectionError, NoOptionError), e:
    pass
PARSEINI
}

MQ_HOST=`read_option mosquitto host`
MQ_USER=`read_option mosquitto user`
MQ_PASSWD=`read_option mosquitto password`
MQ_TOPIC=`read_option mosquitto topic_image`

IMAGE_DATA=`base64 "$1"`
TIMESTAMP=`date +%s`
DATA="{\"image\":\"${IMAGE_DATA}\", \"time\":\"${TIMESTAMP}000\"}"
TMP_FILE=`mktemp -t`
echo "$DATA" > $TMP_FILE

mosquitto_pub -h "$MQ_HOST" -u "$MQ_USER" -P "$MQ_PASSWD" -t "$MQ_TOPIC" -q 2 -f "$TMP_FILE"
rm -f "$TMP_FILE"
