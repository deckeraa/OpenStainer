#![feature(proc_macro_hygiene, decl_macro)]

#[macro_use]
extern crate rocket;

mod structs_and_consts;
mod graphql;
mod motion;

use gpio::{GpioIn, GpioOut};
use rocket::http::{Method};
use rocket::State;
use std::convert::TryFrom;
use std::convert::TryInto;
use std::sync::Mutex;
use std::{thread, time};
use thread_priority::*;
use juniper::{FieldResult};
use rocket_contrib::serve::StaticFiles;
use rocket_cors::{AllowedHeaders, AllowedOrigins};
use std::time::{Instant};
use std::sync::atomic::Ordering;
use std::sync::atomic::*;
use std::process::Command;
pub use crate::structs_and_consts::*;
pub use crate::graphql::*;
pub use crate::motion::*;

#[rocket::post("/pause_procedure")]
fn pause_procedure(pes: State<ProcedureExecutionState>) -> String {
    let pes = pes.inner();
    pes.atm.store(ProcedureExecutionStateEnum::Paused, Ordering::Relaxed);
    format! {"/pause {:?}", pes.atm.load(Ordering::Relaxed)}
}

#[rocket::post("/resume_procedure")]
fn resume_procedure(pes: State<ProcedureExecutionState>) -> String {
    let pes = pes.inner();
    pes.atm.store(ProcedureExecutionStateEnum::Running, Ordering::Relaxed);
    format! {"/pause {:?}", pes.atm.load(Ordering::Relaxed)}
}

#[rocket::get("/read_procedure_status")]
fn read_procedure_status(pes: State<ProcedureExecutionState>) -> String {
    let pes = pes.inner();
    format! {"/read {:?}", pes.atm.load(Ordering::Relaxed)}
}

#[rocket::get("/seconds_remaining")]
fn seconds_remaining(pes: State<ProcedureExecutionState>) -> String {
    let pes = pes.inner();
    format! {"{}", pes.seconds_remaining.load(Ordering::Relaxed)}
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

fn move_steps(pi: &mut Pi, axis: AxisDirection, forward: bool, pulses: u64, is_homing: bool, opt_pes: Option<&ProcedureExecutionState>, skip_soft_estop_check: bool) -> MoveResult {
    let stepper = match axis {
        AxisDirection::X => &mut pi.stepper_x,
        AxisDirection::Z => &mut pi.stepper_z,
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
        Ok(v) => println!("Real Time thread priority set: {:?}", v),
        Err(e) => println!("Real Time thread priority not set: {:?}", e),
    }

    // Enable and set the direction
    stepper.ena.set_low().expect("Couldn't turn on ena"); // logic is reversed to due transistor
    thread::sleep(time::Duration::from_millis(1));
    stepper.dir.set_value(forward).expect("Couldn't set dir");
    thread::sleep(time::Duration::from_millis(1));

    let times = generate_wait_times(pulses, 5760, 15, 17000);
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
        if  bool::from(pi.estop.read_value().unwrap()) {
            hit_e_stop = true;
	    println!("Hit estop!");
	    if opt_pes.is_some() && !skip_soft_estop_check {
		opt_pes.unwrap().atm.store(ProcedureExecutionStateEnum::Paused, Ordering::Relaxed);
		pi.red_light.set_low().expect("Couldn't run off red light.");
	    }
            break;
        }
	// check the software estop
	if opt_pes.is_some() && !skip_soft_estop_check{
	    let state = opt_pes.unwrap().atm.load(Ordering::Relaxed); // == ProcedureExecutionStateEnum::Paused;
	    if state == ProcedureExecutionStateEnum::Paused || state == ProcedureExecutionStateEnum::Stopped {
		hit_e_stop = true;
		break;
	    }
	}

        // generate the pulse
        stepper.pul.set_high().expect("Couldn't set pul");
        thread::sleep(*t);
        stepper.pul.set_low().expect("Couldn't set pul");
        thread::sleep(*t);
    }

    // disable the stepper
    thread::sleep(time::Duration::from_millis(1));
    stepper.ena.set_high().expect("Couldn't turn off ena"); // logic is reversed to due transistor
    thread::sleep(time::Duration::from_millis(1));

    // Drop the thread priority back down since we're done with signal generation.
    let ret = set_thread_priority(
        thread_native_id(),
        ThreadPriority::Min,
        ThreadSchedulePolicy::Normal(NormalThreadSchedulePolicy::Normal),
    );
    match ret {
        Ok(v) => println!("Dropped real-time thread priority: {:?}", v),
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

// #[get("/move/<axis>/<forward>")]
// fn index(pi_state: State<SharedPi>, axis: AxisDirection, forward: bool) -> String {
//     // Grab the lock on the shared pins structure
//     let pi_mutex = &mut pi_state.inner();
//     let pi = &mut *pi_mutex.lock().unwrap();
//     let ret = move_steps(pi, axis, forward, 16000, false, None);
//     format! {"{:?}",ret}
// }

fn home(pi: &mut Pi, opt_pes: Option<&ProcedureExecutionState>) -> MoveResult {
    let ret_one = move_steps(pi, AxisDirection::Z, true, 160000, true, opt_pes, false);
    println!("Result of first home move {:?}",ret_one);
    if ret_one == MoveResult::HitEStop || ret_one == MoveResult::FailedToHome{
	return MoveResult::FailedToHome;
    }
    let ret_two = move_steps(pi, AxisDirection::X, false, 320000, true, opt_pes, false);
    if ret_one == MoveResult::HitLimitSwitch && ret_two == MoveResult::HitLimitSwitch {
        move_to_up_position(pi, opt_pes);
        move_to_left_position(pi, opt_pes);
    }
    ret_two
}

#[post("/home")]
fn home_handler(pi_state: State<SharedPi>, pes: State<ProcedureExecutionState>) -> String {
    let pi_mutex = &mut pi_state.inner();
    let pi = &mut *pi_mutex.lock().unwrap();
    let pes = pes.inner();
    let ret = home(pi, Some(pes));
    format! {"{:?}",ret}
    // let ret_one = move_steps(pi, AxisDirection::Z, true, 160000, true);
    // let ret_two = move_steps(pi, AxisDirection::X, false, 320000, true);
    // if ret_one == MoveResult::HitLimitSwitch && ret_two == MoveResult::HitLimitSwitch {
    //     move_to_up_position(pi);
    //     move_to_left_position(pi);
    // }
    // format! {"{:?} {:?}",ret_one,ret_two}
}

#[post("/run_procedure/<id>")]
fn run_procedure(pi_state: State<SharedPi>, pes: State<ProcedureExecutionState>, id: String) -> String {
    let pes = pes.inner();
    if pes.atm.load(Ordering::Relaxed) == ProcedureExecutionStateEnum::Running ||
	pes.atm.load(Ordering::Relaxed) == ProcedureExecutionStateEnum::Paused {
	    println!("Already running a procedure. Exiting.");
	    return "Already running a procedure. Exiting.".to_string();
	}

    let pi_mutex = &mut pi_state.inner();

    pes.atm.store(ProcedureExecutionStateEnum::Running, Ordering::Relaxed);

    // load the procedure
    let proc : FieldResult<Procedure> = procedure_by_id(id);
    if proc.is_err() { return format! {"Couldn't find procedure with that ID."}; }
    let proc = proc.unwrap();

    {
	let pi = &mut *pi_mutex.lock().unwrap();
	pi.current_procedure = Some(proc.clone());
	// initialize the run status
	pi.run_status = Some(ProcedureRunStatus {
	    current_procedure_step_number : 0,
	    current_cycle_number: 0,
	    run_state: ProcedureExecutionStateEnum::Running,
	});
	pi.red_light.set_high().expect("Couldn't turn on estop light.");
    }

    let num_repeats = match proc.repeat {
	Some(v) => v,
	None => 1
    };
    // loop over repeats
    for repeat_num in 0..num_repeats {
	println!("Repeat #: {}",num_repeats);
	// loop over steps
	println!("proc.procedure_steps: {:?}", proc.procedure_steps);
	for (index,step) in proc.procedure_steps.iter().enumerate() {
	    println!("Trying to grab the lock.");
	    // grab the lock
	    {
		let pi = &mut *pi_mutex.lock().unwrap();
		let run_status = pi.run_status.as_mut().unwrap();
		run_status.current_cycle_number = repeat_num + 1;
		run_status.current_procedure_step_number = (index + 1).try_into().unwrap();
		//pi.run_status.current_procedure_step_number = pi.run_status.current_procedure_step_number
		println!("Step: {:?}",step);
		// move to the jar
		let mut got_to_jar = false;
		println!("Entering loop B");
		while !got_to_jar {
		    if pes.atm.load(Ordering::Relaxed) == ProcedureExecutionStateEnum::Running {
			println!("============== Running move_to_jar {:?} ", step.jar_number);
			let ret = move_to_jar( pi, step.jar_number, Some(&pes) );
			if ret == MoveResult::MovedFullDistance {
			    got_to_jar = true;
			    break;
			}
		    }
		    if pes.atm.load(Ordering::Relaxed) == ProcedureExecutionStateEnum::Paused {
			pi.green_light.set_high().expect("Couldn't turn green light on");
			if bool::from(pi.green_button.read_value().unwrap()) {
			    pes.atm.store(ProcedureExecutionStateEnum::Running, Ordering::Relaxed);
			    pi.green_light.set_low().expect("Couldn't turn green light off");
			    pi.red_light.set_high().expect("Couldn't turn red light back on");
			}
		    }
		    thread::sleep(time::Duration::from_millis(10));
		}
	    }
	    println!("Exited loop B");

	    //pi.run_status.current_procedure_step_start_instant = Instant::now();
	    let mut start_instant = Instant::now();
	    let mut us_remaining : u128 = (step.time_in_seconds * 1000 * 1000).try_into().unwrap();

	    // sleep until it's time to move again
	    println!("Entering loop C");
	    while us_remaining > 0 {
		if pes.atm.load(Ordering::Relaxed) == ProcedureExecutionStateEnum::Running {
		    // update the timer controls
		    let elapsed_us = start_instant.elapsed().as_micros();
		    start_instant = Instant::now();
		    if elapsed_us > us_remaining { // avoid attempts to subtract with overflow
			us_remaining = 0;
		    }
		    else {
			us_remaining = us_remaining - elapsed_us;
		    }

		    // update the PES to inform the client how many seconds are remaining
		    pes.seconds_remaining.store((us_remaining / (1000 * 1000)).try_into().unwrap(), Ordering::Relaxed);
		}
		// grab the lock
		{
		    let pi = &mut *pi_mutex.lock().unwrap();
		    // check stop button
		    if bool::from(pi.estop.read_value().unwrap()) {
			pes.atm.store(ProcedureExecutionStateEnum::Paused, Ordering::Relaxed);
		    }
		    
		    if pes.atm.load(Ordering::Relaxed) == ProcedureExecutionStateEnum::Paused {
			// handle run/pause buttons
			pi.green_light.set_high().expect("Couldn't turn green light on");
			pi.red_light.set_low().expect("Couldn't turn red light off");
			if bool::from(pi.green_button.read_value().unwrap()) {
			    pes.atm.store(ProcedureExecutionStateEnum::Running, Ordering::Relaxed);
			    pi.green_light.set_low().expect("Couldn't turn green light off");
			    pi.red_light.set_high().expect("Couldn't turn red light back on");
			    start_instant = Instant::now();
			}
		    }
		}
		thread::sleep(time::Duration::from_millis(20));
	    }

	    // // sleep until it's time to move again
	    // println!("Entering loop C");
	    // while start_instant.elapsed().as_secs() < seconds_remaining {
	    // 	thread::sleep(time::Duration::from_millis(200));
	    // }
	    println!("Exited loop C");
	}
    }

    // End of procedure, so move to the up position
    {
	let pi = &mut *pi_mutex.lock().unwrap();
	let ret = move_to_up_position( pi, None );
	if ret == MoveResult::HitEStop {
	    return format! {"Stopped due to e-stop being hit."}
	}
	pi.run_status = None;
	pi.red_light.set_low().expect("Couldn't turn off estop light.");
    }
    pes.atm.store(ProcedureExecutionStateEnum::Completed, Ordering::Relaxed);
    println!("========= Done running procedure ========");
    format! {"run_procedure return value TODO"}
}

#[post("/move_by_pulses/<axis>/<forward>/<pulses>")]
fn move_by_pulses(
    pi_state: State<SharedPi>,
    pes: State<ProcedureExecutionState>,
    axis: AxisDirection,
    forward: bool,
    pulses: PulseCount,
) -> String {
    let pi_mutex = &mut pi_state.inner();
    let pi = &mut *pi_mutex.lock().unwrap();
    let pes = pes.inner();
    let ret = move_steps(pi, axis, forward, pulses, false, Some(pes), false);
    format! {"{:?}",ret}
}

#[post("/move_by_inches/<axis>/<forward>/<inches>")]
fn move_by_inches(pi_state: State<SharedPi>, pes: State<ProcedureExecutionState>, axis: AxisDirection, forward: bool, inches: Inch) -> String {
    let pi_mutex = &mut pi_state.inner();
    let pi = &mut *pi_mutex.lock().unwrap();
    let stepper = get_stepper(pi, &axis);
    let pulses = inches_to_pulses(inches, stepper);
    let pes = pes.inner();
    let ret = move_steps(pi, axis, forward, pulses, false, Some(pes), false);
    format! {"{:?}",ret}
}

fn move_to_pos(pi: &mut Pi, axis: AxisDirection, inches: Inch, opt_pes: Option<&ProcedureExecutionState>, skip_soft_estop_check: bool) -> MoveResult {
    println!("move_to_pos axis: {}  inches: {}", axis, inches);
    let is_not_homed = get_stepper(pi, &axis).pos.is_none();
    
    if is_not_homed {
	let ret = home(pi, opt_pes);
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
    move_steps(pi, axis, forward, pulses, false, opt_pes, skip_soft_estop_check)
}

#[post("/move_to_pos/<axis>/<inches>")]
fn move_to_pos_handler(pi_state: State<SharedPi>, pes: State<ProcedureExecutionState>, axis: AxisDirection, inches: Inch) -> String {
    let pi_mutex = &mut pi_state.inner();
    let pi = &mut *pi_mutex.lock().unwrap();
    let pes = pes.inner();
    let ret = move_to_pos(pi, axis, inches, Some(pes), false);
    format! {"{:?}",ret}
}

fn move_to_up_position(pi: &mut Pi, opt_pes: Option<&ProcedureExecutionState>) -> MoveResult {
    move_to_pos(pi, AxisDirection::Z, UP_POSITION, opt_pes, true)
}

#[post("/move_to_up_position")]
fn move_to_up_position_handler(pi_state: State<SharedPi>, pes: State<ProcedureExecutionState>) -> String {
    let pi_mutex = &mut pi_state.inner();
    let pi = &mut *pi_mutex.lock().unwrap();
    let pes = pes.inner();
    let ret = move_to_up_position(pi, Some(pes));
    format! {"{:?}",ret}
}

fn move_to_down_position(pi: &mut Pi, opt_pes: Option<&ProcedureExecutionState>) -> MoveResult {
    move_to_pos(pi, AxisDirection::Z, 0.0, opt_pes, true)
}

#[post("/move_to_down_position")]
fn move_to_down_position_handler(pi_state: State<SharedPi>, pes: State<ProcedureExecutionState>) -> String {
    let pi_mutex = &mut pi_state.inner();
    let pi = &mut *pi_mutex.lock().unwrap();
    let pes = pes.inner();
    let ret = move_to_down_position(pi, Some(pes));
    format! {"{:?}",ret}
}

fn move_to_left_position(pi: &mut Pi, opt_pes: Option<&ProcedureExecutionState>) -> MoveResult {
    move_to_pos(pi, AxisDirection::X, LEFT_POSITION, opt_pes, false)
}

#[post("/move_to_left_position")]
fn move_to_left_position_handler(pi_state: State<SharedPi>, pes: State<ProcedureExecutionState>) -> String {
    let pi_mutex = &mut pi_state.inner();
    let pi = &mut *pi_mutex.lock().unwrap();
    let pes = pes.inner();
    let ret = move_to_left_position(pi, Some(pes));
    format! {"{:?}",ret}
}

fn known_to_be_at_jar_position(pi: &mut Pi, jar_number: i32) -> bool {
    if pi.stepper_x.pos.is_none() {
	return false;
    }
    let current_inches : Inch = pulses_to_inches(pi.stepper_x.pos.unwrap(), &pi.stepper_x);
    let target_inches  : Inch = LEFT_POSITION + JAR_SPACING * (jar_number - 1) as f64;
    (target_inches - current_inches).abs() < 0.1
}

fn move_to_jar(pi: &mut Pi, jar_number: i32, opt_pes: Option<&ProcedureExecutionState>) -> MoveResult {
    if !known_to_be_at_jar_position(pi, jar_number) {
	let ret: MoveResult = move_to_up_position(pi, opt_pes);
	if ret == MoveResult::HitLimitSwitch
            || ret == MoveResult::HitEStop
            || ret == MoveResult::FailedDueToNotHomed
	{
            return ret;
	}
	let ret = move_to_pos(
            pi,
            AxisDirection::X,
            LEFT_POSITION + JAR_SPACING * (jar_number - 1) as f64,
	    opt_pes,
	    false
	);
	if ret == MoveResult::HitLimitSwitch
            || ret == MoveResult::HitEStop
            || ret == MoveResult::FailedDueToNotHomed
	{
            return ret;
	}
    }
    let ret = move_to_down_position(pi, opt_pes);
    ret
}

#[post("/move_to_jar/<jar_number>")]
fn move_to_jar_handler(pi_state: State<SharedPi>, pes: State<ProcedureExecutionState>, jar_number: i32) -> String {
    let pi_mutex = &mut pi_state.inner();
    let pes = pes.inner();
    let pi = &mut *pi_mutex.lock().unwrap();
    println!("0.1: {}", jar_number);
    let ret = move_to_jar(pi, jar_number, Some(pes));
    format! {"{:?}",ret}
}

#[get("/pos/<axis>")]
fn pos(pi: State<SharedPi>, axis: AxisDirection) -> String {
    let pi = &mut *pi.inner().lock().unwrap();
    let stepper = match axis {
        AxisDirection::X => &mut pi.stepper_x,
        AxisDirection::Z => &mut pi.stepper_z,
    };
    match stepper.pos {
        Some(v) => format!("{}", pulses_to_inches(v, stepper)),
        None => "Not homed".to_string(),
    }
}

#[get("/couch")]
fn couch() -> String {
    let resp = reqwest::blocking::get(reqwest::Url::parse(format!("{}/_design/procedures/_view/procedures?include_docs=true",COUCHDB_URL).as_str()).unwrap());
    if resp.is_ok() {
	let json = resp.unwrap().json::<ViewResult<Procedure>>().unwrap();
	let s : String = serde_json::to_string(&json).unwrap();
	return s;
    }
    return "Couldn't query the view".to_string();
}

#[post("/exit_kiosk_mode")]
fn exit_kiosk_mode() -> String {
    let mut command = Command::new("./exit_kiosk_mode.sh");
    let result = match command.status() {
	Ok(r) => r.to_string(),
	Err(e) => e.to_string(),
    };
    format!("{:?} {}",command, result)
}



fn main() {
    let shared_pi = Mutex::new(Pi {
        estop: gpio::sysfs::SysFsGpioInput::open(25).unwrap(),
	green_button: gpio::sysfs::SysFsGpioInput::open(18).unwrap(),
	red_light: gpio::sysfs::SysFsGpioOutput::open(24).unwrap(),
	green_light: gpio::sysfs::SysFsGpioOutput::open(23).unwrap(),
        stepper_x: Stepper {
            ena: gpio::sysfs::SysFsGpioOutput::open(2).unwrap(),
            dir: gpio::sysfs::SysFsGpioOutput::open(4).unwrap(),
            pul: gpio::sysfs::SysFsGpioOutput::open(17).unwrap(),
            limit_switch_low: Some(gpio::sysfs::SysFsGpioInput::open(14).unwrap()),
            limit_switch_high: None,
            pos: None,
            position_limit: 10.0,
            pulses_per_revolution: 800,
            travel_distance_per_turn: 0.063,
        },
        stepper_z: Stepper {
            ena: gpio::sysfs::SysFsGpioOutput::open(3).unwrap(),
            dir: gpio::sysfs::SysFsGpioOutput::open(27).unwrap(),
            pul: gpio::sysfs::SysFsGpioOutput::open(22).unwrap(),
            limit_switch_low: None,
            limit_switch_high: Some(gpio::sysfs::SysFsGpioInput::open(15).unwrap()),
            pos: None,
            position_limit: 3.75,
            pulses_per_revolution: 800,
            travel_distance_per_turn: 0.063,
        },
	current_procedure: None,
	run_status: None,
    });

    let atm : AtomicProcedureExecutionStateEnum = AtomicProcedureExecutionStateEnum::new(ProcedureExecutionStateEnum::NotStarted);
    let pes : ProcedureExecutionState = ProcedureExecutionState { atm: atm, seconds_remaining: AtomicU64::new(0)};

    {
	// initialize enable pins (this is needed since the logic is reversed since it's behind
	// a transistor. The reason it is behind a transistor is because the pins are automatically
	// set to pulled-up inputs on Pi boot.
	let pi = &mut *shared_pi.lock().unwrap();
	pi.stepper_x.ena.set_high().expect("Couldn't set enable pin"); // high is low since it's behind a transistor
	pi.stepper_z.ena.set_high().expect("Couldn't set enable pin"); // high is low since it's behind a transistor
    }
    
    // set up CORS
    let allowed_origins = AllowedOrigins::all();

    // You can also deserialize this
    let cors = rocket_cors::CorsOptions {
        allowed_origins,
        allowed_methods: vec![Method::Get, Method::Put, Method::Post, Method::Delete].into_iter().map(From::from).collect(),
        allowed_headers: AllowedHeaders::some(&["Authorization", "Accept","X-Requested-With","Content-Type","Cache-Control"]),
        allow_credentials: true,
        ..Default::default()
    }
    .to_cors().unwrap();

    rocket::ignite()
        .manage(shared_pi)
	.manage(Schema::new(Query, Mutation))
	.manage(pes)
        .mount(
            "/",
            routes![
//                index,
                home_handler,
                pos,
                move_by_pulses,
                move_by_inches,
                move_to_pos_handler,
                move_to_up_position_handler,
                move_to_down_position_handler,
                move_to_left_position_handler,
                move_to_jar_handler,
		graphiql,
		post_graphql_handler,
		couch,
		// delete_procedure,
		run_procedure,
		pause_procedure,
		resume_procedure,
		read_procedure_status,
		seconds_remaining,
		exit_kiosk_mode,
            ],)
	.mount("/",StaticFiles::from("./resources/public/"))
	.attach(cors)
        .launch();
}
