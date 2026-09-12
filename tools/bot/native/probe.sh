#!/system/bin/sh
read -r rate count < /sdcard/Android/data/cube.run/files/bot-input-rate.txt
exec /data/local/tmp/cube-run-bot-input /dev/input/event1 "$rate" "$count"
