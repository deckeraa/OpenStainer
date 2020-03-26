#!/bin/bash

# Close the currently running program (that's in kiosk mode)
killall chromium-browse

# Start it again, this time not in kiosk mode.
# Note that since the Rust web server is running as root, we need to
# run this as non-root to avoid chromium complaining about needing to run
# as no-sandbox mode.
# We pass in DISPLAY=:0.0 since the default display leads to xauth issues when running as non-root.
su pi -c "DISPLAY=:0.0 chromium-browser http://localhost:8000/index.html --app=localhost"
