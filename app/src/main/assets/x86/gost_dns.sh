#!/system/bin/sh

DIR=$1
DNS=$2

PATH=$DIR:$PATH

gost -L dns://:53/$DNS &> $DIR/gost_dns.log &
echo "$!" > $DIR/gost_dns.pid