#!/bin/bash

# CouchDB should already be running (started in /etc/rc.local).

# Start Real-time Motion Rust Micro-service
sudo ./target/debug/rmrm &

# Start the Clojure web & GraphQL server
java -jar ./target/slide-stainer-0.1.0-SNAPSHOT-standalone.jar &

# Turn off power saving and the screen saver
# xset -dpms
# xset s off
# xset s noblank

unclutter &

# Open up the web app in Chromium
chromium-browser ./resources/public/index.html &
