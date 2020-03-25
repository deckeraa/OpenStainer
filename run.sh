#!/bin/bash

# CouchDB should already be running (started in /etc/rc.local before this gets called).

cd /home/pi/code/slide-stainer

# Start Rust web server
sudo ./target/debug/rmrm &

# Turn off power saving and the screen saver
xset -dpms
xset s off
xset s noblank

unclutter &

# The web app gets opened up in /etc/xdg/lxsession/LXDE-pi/autostart when the graphical session starts
