#!/system/bin/sh

DIR=$1
SRC=$2
DST=$3
KIND=$4

PATH=$DIR:$PATH

gost $SRC $DST &> $DIR/gost_$KIND.log &
echo "$!" > $DIR/gost_$KIND.pid