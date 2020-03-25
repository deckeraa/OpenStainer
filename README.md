# slide-stainer

The OpenStainer is open-source, open-hardware automated slide stainer for laboratory use (e.g. histology, nanotech research).

Note: the OpenStainer is not yet released. This message will be removed from the README when the project has shipped it's 1.0 version.

## Development Mode

### Run application:
Boot an RT-PREEMPT-patched Linux distro on a Raspberry Pi.
The RT-PREEMPT patch is needed for smooth motor control since the stepper control signal is generated directly on the Pi.

To build the Rust web server (./src/main.rs),
you'll need to use Nightly Rust, due to some crates (namely Rocket) that require it:
```
rustup override set nightly
```

Then build and run the binary.
The binary must be run with sudoer priveleges since it sets thread priority to real-time for purposes of motion control.
```
cargo build && sudo ./target/debug/rmrm
```

Finally, start up the Clojurescript front-end

```
lein figwheel dev devcards
```

Figwheel will automatically push cljs changes to the browser.

Wait a bit, then browse to [http://localhost:3449](http://localhost:3449).

## Configuring the software to auto-start on Pi boot
In /etc/rc.local, place the following:
```
su -l pi /home/pi/code/slide-stainer/run.sh &
```

In /etc/xdg/lxsession/LXDE-pi/autostart, place the following:
```
chromium-browser http://localhost:8000/index.html --kiosk
```

## Production Build

```
lein clean
lein cljsbuild once min
```
