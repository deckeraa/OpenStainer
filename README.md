# slide-stainer

A [reagent](https://github.com/reagent-project/reagent) application designed to ... well, that part is up to you.

## Development Mode

### Run application:

To build the Real-time Motion Rust Micro-service (./src/main.rs),
you'll need to use Nightly Rust, due to some crates (namely Rocket) that require it:
```
rustup override set nightly
```

Then build and run the binary.
The binary must be run with sudoer priveleges since it sets thread priority to real-time.
```
cargo build && sudo ./target/debug/rmrm
```

// Next, start up the Clojure backend:
// ```
// lein ring server-headless
// ```

Finally, start up the Clojurescript front-end

```
lein figwheel dev devcards
```

Figwheel will automatically push cljs changes to the browser.

Wait a bit, then browse to [http://localhost:3449](http://localhost:3449).

## Auto-start
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
