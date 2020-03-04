#![feature(proc_macro_hygiene, decl_macro)]

#[macro_use]
extern crate rocket;

use gpio::{GpioIn, GpioOut};
use rocket::http::RawStr;
use rocket::request::FromParam;
use rocket::State;
use std::convert::TryFrom;
use std::convert::TryInto;
use std::fmt;
use std::sync::Mutex;
use std::{thread, time};
use thread_priority::*;

const LEFT_POSITION: Inch = 0.35;
const UP_POSITION: Inch = 3.5;
const JAR_SPACING: Inch = 1.9;

type PulseCount = u64;
type Inch = f64;

struct Pi {
    stepper_x: Stepper,
    stepper_z: Stepper,
    estop: gpio::sysfs::SysFsGpioInput,
}

struct Stepper {
    ena: gpio::sysfs::SysFsGpioOutput,
    dir: gpio::sysfs::SysFsGpioOutput,
    pul: gpio::sysfs::SysFsGpioOutput,
    limit_switch_low: Option<gpio::sysfs::SysFsGpioInput>,
    limit_switch_high: Option<gpio::sysfs::SysFsGpioInput>,
    pos: Option<PulseCount>,
    position_limit: Inch,
    pulses_per_revolution: u64,
    travel_distance_per_turn: Inch,
}

type SharedPi = Mutex<Pi>;

#[derive(Debug, PartialEq, Eq)]
enum MoveResult {
    MovedFullDistance,
    HitLimitSwitch,
    HitEStop,
    FailedDueToNotHomed,
    FailedToHome,
}

enum Axis {
    X,
    Z,
}

impl<'r> FromParam<'r> for Axis {
    type Error = &'r RawStr;

    fn from_param(param: &'r RawStr) -> Result<Self, Self::Error> {
        match param.as_str() {
            "x" => Ok(Axis::X),
            "z" => Ok(Axis::Z),
            _ => Err(param),
        }
    }
}

impl fmt::Display for Axis {
    fn fmt(&self, f: &mut fmt::Formatter<'_>) -> fmt::Result {
        match self {
            Axis::X => write!(f, "x"),
            Axis::Z => write!(f, "z"),
        }
    }
}

fn get_stepper<'a>(pi: &'a mut Pi, axis: &Axis) -> &'a mut Stepper {
    match axis {
        Axis::X => &mut pi.stepper_x,
        Axis::Z => &mut pi.stepper_z,
    }
}

fn inches_to_pulses(inches: Inch, stepper: &Stepper) -> PulseCount {
    (stepper.pulses_per_revolution as f64 * inches / stepper.travel_distance_per_turn) as u64
}

fn pulses_to_inches(pulses: PulseCount, stepper: &Stepper) -> Inch {
    pulses as f64 * stepper.travel_distance_per_turn / stepper.pulses_per_revolution as f64
}

fn generate_wait_times(
    size: u64,
    start_hz: u64,
    slope: u64,
    max_hz: u64,
) -> Vec<std::time::Duration> {
    let mut times = vec![time::Duration::from_nanos(0); size.try_into().unwrap()];
    if size == 0 {
        return times;
    }

    let halfway = size / 2;
    for i in 0..halfway + 1 {
        let hz = std::cmp::min(start_hz + slope * i, max_hz);
        times[usize::try_from(i).unwrap()] = time::Duration::from_nanos(1_000_000_000 / hz);
    }
    for i in halfway..size {
        times[usize::try_from(i).unwrap()] = times[usize::try_from(size - i - 1).unwrap()];
    }
    times
}

fn move_steps(pi: &mut Pi, axis: Axis, forward: bool, pulses: u64, is_homing: bool) -> MoveResult {
    let stepper = match axis {
        Axis::X => &mut pi.stepper_x,
        Axis::Z => &mut pi.stepper_z,
    };

    let pos: Option<u64> = stepper.pos;

    if !is_homing && pos.is_none() {
        return MoveResult::FailedDueToNotHomed;
    }

    // Set the thread to real-time since we're generating a signal that preferably is smooth.
    let ret = set_thread_priority(
        thread_native_id(),
        ThreadPriority::Specific(50),
        ThreadSchedulePolicy::Realtime(RealtimeThreadSchedulePolicy::Fifo),
    );
    match ret {
        Ok(v) => println!("Ok: {:?}", v),
        Err(e) => println!("Err: {:?}", e),
    }

    // Enable and set the direction
    stepper.ena.set_high().expect("Couldn't turn on ena");
    thread::sleep(time::Duration::from_millis(1));
    stepper.dir.set_value(forward).expect("Couldn't set dir");
    thread::sleep(time::Duration::from_millis(1));

    let times = generate_wait_times(pulses, 180, 15, 17000);
    let mut hit_limit_switch = false;
    let mut hit_e_stop = false;

    let mut moved_pulses = 0;
    for t in times.iter() {
        moved_pulses = moved_pulses + 1;
        // check limit switch // TODO add calculated limit checks for the other side
        // check lower limit switch
        if !forward
            && stepper.limit_switch_low.is_some()
            && bool::from(
                (*stepper.limit_switch_low.as_mut().unwrap())
                    .read_value()
                    .unwrap(),
            )
        {
            hit_limit_switch = true;
            break;
        }
        if forward
            && stepper.limit_switch_high.is_some()
            && bool::from(
                (*stepper.limit_switch_high.as_mut().unwrap())
                    .read_value()
                    .unwrap(),
            )
        {
            hit_limit_switch = true;
            break;
        }

        // check estop
        if bool::from(pi.estop.read_value().unwrap()) {
            hit_e_stop = true;
            break;
        }

        // generate the pulse
        stepper.pul.set_high().expect("Couldn't set pul");
        thread::sleep(*t);
        stepper.pul.set_low().expect("Couldn't set pul");
        thread::sleep(*t);
    }

    // disable the stepper
    thread::sleep(time::Duration::from_millis(1));
    stepper.ena.set_low().expect("Couldn't turn off ena");
    thread::sleep(time::Duration::from_millis(1));

    // Drop the thread priority back down since we're done with signal generation.
    let ret = set_thread_priority(
        thread_native_id(),
        ThreadPriority::Min,
        ThreadSchedulePolicy::Normal(NormalThreadSchedulePolicy::Normal),
    );
    match ret {
        Ok(v) => println!("Ok: {:?}", v),
        Err(e) => println!("Err: {:?}", e),
    }

    // update position
    if hit_limit_switch {
        // TODO this assumes it's a lower limit switch
        if !forward {
            stepper.pos = Some(0);
        } else {
            stepper.pos = Some(
                (stepper.position_limit * stepper.pulses_per_revolution as f64
                    / stepper.travel_distance_per_turn) as u64,
            );
        }
    } else if is_homing {
        return MoveResult::FailedToHome;
    }

    let v = stepper.pos.expect("Unreachable codepath in move_steps");

    if !hit_limit_switch {
        println!(
            "Updating pi.stepper_x.pos to {:?}",
            Some(if forward { v + pulses } else { v - pulses })
        );
        stepper.pos = Some(if forward {
            v + moved_pulses
        } else {
            v - moved_pulses
        });
    }

    // return the proper result for what happened
    if hit_limit_switch {
        return MoveResult::HitLimitSwitch;
    }
    if hit_e_stop {
        return MoveResult::HitEStop;
    }
    MoveResult::MovedFullDistance
}

#[get("/move/<axis>/<forward>")]
fn index(pi_state: State<SharedPi>, axis: Axis, forward: bool) -> String {
    // Grab the lock on the shared pins structure
    let pi_mutex = &mut pi_state.inner();
    let pi = &mut *pi_mutex.lock().unwrap();
    let ret = move_steps(pi, axis, forward, 16000, false);
    format! {"{:?}",ret}
}

fn home(pi: &mut Pi) -> MoveResult {
    let ret_one = move_steps(pi, Axis::Z, true, 160000, true);
    let ret_two = move_steps(pi, Axis::X, false, 320000, true);
    if ret_one == MoveResult::HitLimitSwitch && ret_two == MoveResult::HitLimitSwitch {
        move_to_up_position(pi);
        move_to_left_position(pi);
    }
    ret_two
}

#[get("/home")]
fn home_handler(pi_state: State<SharedPi>) -> String {
    let pi_mutex = &mut pi_state.inner();
    let pi = &mut *pi_mutex.lock().unwrap();
    let ret = home(pi);
    format! {"{:?}",ret}
    // let ret_one = move_steps(pi, Axis::Z, true, 160000, true);
    // let ret_two = move_steps(pi, Axis::X, false, 320000, true);
    // if ret_one == MoveResult::HitLimitSwitch && ret_two == MoveResult::HitLimitSwitch {
    //     move_to_up_position(pi);
    //     move_to_left_position(pi);
    // }
    // format! {"{:?} {:?}",ret_one,ret_two}
}

#[get("/move_by_pulses/<axis>/<forward>/<pulses>")]
fn move_by_pulses(
    pi_state: State<SharedPi>,
    axis: Axis,
    forward: bool,
    pulses: PulseCount,
) -> String {
    let pi_mutex = &mut pi_state.inner();
    let pi = &mut *pi_mutex.lock().unwrap();
    let ret = move_steps(pi, axis, forward, pulses, false);
    format! {"{:?}",ret}
}

#[get("/move_by_inches/<axis>/<forward>/<inches>")]
fn move_by_inches(pi_state: State<SharedPi>, axis: Axis, forward: bool, inches: Inch) -> String {
    let pi_mutex = &mut pi_state.inner();
    let pi = &mut *pi_mutex.lock().unwrap();
    let stepper = get_stepper(pi, &axis);
    let pulses = inches_to_pulses(inches, stepper);
    let ret = move_steps(pi, axis, forward, pulses, false);
    format! {"{:?}",ret}
}

fn move_to_pos(pi: &mut Pi, axis: Axis, inches: Inch) -> MoveResult {
    println!("move_to_pos axis: {}  inches: {}", axis, inches);
    let is_not_homed = get_stepper(pi, &axis).pos.is_none();
    
    if is_not_homed {
	let ret = home(pi);
	if ret == MoveResult::FailedToHome {
	    return MoveResult::FailedDueToNotHomed;
	}
    }
    
    let stepper = get_stepper(pi, &axis);
    let cur_pos = stepper.pos.unwrap();
    let dest_pos = inches_to_pulses(inches, stepper);
    let forward = cur_pos < dest_pos;
    let pulses = if cur_pos < dest_pos {
        dest_pos - cur_pos
    } else {
        cur_pos - dest_pos
    };
    move_steps(pi, axis, forward, pulses, false)
}

#[get("/move_to_pos/<axis>/<inches>")]
fn move_to_pos_handler(pi_state: State<SharedPi>, axis: Axis, inches: Inch) -> String {
    let pi_mutex = &mut pi_state.inner();
    let pi = &mut *pi_mutex.lock().unwrap();
    let ret = move_to_pos(pi, axis, inches);
    format! {"{:?}",ret}
}

fn move_to_up_position(pi: &mut Pi) -> MoveResult {
    move_to_pos(pi, Axis::Z, UP_POSITION)
}

#[get("/move_to_up_position")]
fn move_to_up_position_handler(pi_state: State<SharedPi>) -> String {
    let pi_mutex = &mut pi_state.inner();
    let pi = &mut *pi_mutex.lock().unwrap();
    let ret = move_to_up_position(pi);
    format! {"{:?}",ret}
}

fn move_to_down_position(pi: &mut Pi) -> MoveResult {
    move_to_pos(pi, Axis::Z, 0.0)
}

#[get("/move_to_down_position")]
fn move_to_down_position_handler(pi_state: State<SharedPi>) -> String {
    let pi_mutex = &mut pi_state.inner();
    let pi = &mut *pi_mutex.lock().unwrap();
    let ret = move_to_down_position(pi);
    format! {"{:?}",ret}
}

fn move_to_left_position(pi: &mut Pi) -> MoveResult {
    move_to_pos(pi, Axis::X, LEFT_POSITION)
}

#[get("/move_to_left_position")]
fn move_to_left_position_handler(pi_state: State<SharedPi>) -> String {
    let pi_mutex = &mut pi_state.inner();
    let pi = &mut *pi_mutex.lock().unwrap();
    let ret = move_to_left_position(pi);
    format! {"{:?}",ret}
}

fn move_to_jar(pi: &mut Pi, jar_number: u16) -> MoveResult {
    let ret: MoveResult = move_to_up_position(pi);
    if ret == MoveResult::HitLimitSwitch
        || ret == MoveResult::HitEStop
        || ret == MoveResult::FailedDueToNotHomed
    {
        return ret;
    }
    let ret = move_to_pos(
        pi,
        Axis::X,
        LEFT_POSITION + JAR_SPACING * (jar_number - 1) as f64,
    );
    if ret == MoveResult::HitLimitSwitch
        || ret == MoveResult::HitEStop
        || ret == MoveResult::FailedDueToNotHomed
    {
        return ret;
    }
    let ret = move_to_down_position(pi);
    ret
}

#[get("/move_to_jar/<jar_number>")]
fn move_to_jar_handler(pi_state: State<SharedPi>, jar_number: u16) -> String {
    let pi_mutex = &mut pi_state.inner();
    let pi = &mut *pi_mutex.lock().unwrap();
    println!("0.1: {}", jar_number);
    let ret = move_to_jar(pi, jar_number);
    format! {"{:?}",ret}
}

#[get("/pos/<axis>")]
fn pos(pi: State<SharedPi>, axis: Axis) -> String {
    let pi = &mut *pi.inner().lock().unwrap();
    let stepper = match axis {
        Axis::X => &mut pi.stepper_x,
        Axis::Z => &mut pi.stepper_z,
    };
    match stepper.pos {
        Some(v) => format!("{}", pulses_to_inches(v, stepper)),
        None => "Not homed".to_string(),
    }
}

fn main() {
    let shared_pi = Mutex::new(Pi {
        estop: gpio::sysfs::SysFsGpioInput::open(25).unwrap(),
        stepper_x: Stepper {
            ena: gpio::sysfs::SysFsGpioOutput::open(2).unwrap(),
            dir: gpio::sysfs::SysFsGpioOutput::open(3).unwrap(),
            pul: gpio::sysfs::SysFsGpioOutput::open(4).unwrap(),
            limit_switch_low: Some(gpio::sysfs::SysFsGpioInput::open(14).unwrap()),
            limit_switch_high: None,
            pos: None,
            position_limit: 10.0,
            pulses_per_revolution: 800,
            travel_distance_per_turn: 0.063,
        },
        stepper_z: Stepper {
            ena: gpio::sysfs::SysFsGpioOutput::open(17).unwrap(),
            dir: gpio::sysfs::SysFsGpioOutput::open(27).unwrap(),
            pul: gpio::sysfs::SysFsGpioOutput::open(22).unwrap(),
            limit_switch_low: None,
            limit_switch_high: Some(gpio::sysfs::SysFsGpioInput::open(15).unwrap()),
            pos: None,
            position_limit: 3.75,
            pulses_per_revolution: 800,
            travel_distance_per_turn: 0.063,
        },
    });
    rocket::ignite()
        .manage(shared_pi)
        .mount(
            "/",
            routes![
                index,
                home_handler,
                pos,
                move_by_pulses,
                move_by_inches,
                move_to_pos_handler,
                move_to_up_position_handler,
                move_to_down_position_handler,
                move_to_left_position_handler,
                move_to_jar_handler
            ],
        )
        .launch();
}
