#![feature(proc_macro_hygiene, decl_macro)]

#[macro_use]
extern crate rocket;

mod structs_and_consts;
mod graphql;
mod motion;
mod couchdb;

use gpio::{GpioIn, GpioOut};
use rocket::http::{Method};
use rocket::State;
use std::sync::Mutex;
use std::convert::TryInto;
use std::{thread, time};
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
pub use crate::couchdb::*;

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

#[post("/home")]
fn home_handler(pi_state: State<SharedPi>, pes: State<ProcedureExecutionState>) -> String {
    let pi_mutex = &mut pi_state.inner();
    let pi = &mut *pi_mutex.lock().unwrap();
    let pes = pes.inner();
    let ret = home(pi, Some(pes));
    format! {"{:?}",ret}
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
    let mut proc = proc.unwrap();

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
		println!("Entering loop B");
		loop {
		    if pes.atm.load(Ordering::Relaxed) == ProcedureExecutionStateEnum::Running {
			println!("============== Running move_to_jar {:?} ", step.jar_number);
			pi.green_light.set_low().expect("Couldn't turn green light off");
			pi.red_light.set_high().expect("Couldn't turn red light back on");
			let ret = move_to_jar( pi, step.jar_number, Some(&pes) );
			if ret == MoveResult::MovedFullDistance {
			    break;
			}
		    }
		    if pes.atm.load(Ordering::Relaxed) == ProcedureExecutionStateEnum::Paused {
			pi.green_light.set_high().expect("Couldn't turn green light on");
			pi.red_light.set_low().expect("Couldn't turn red light off");
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
	// let ret = move_to_up_position( pi, None );
	// if ret == MoveResult::HitEStop {
	//     return format! {"Stopped due to e-stop being hit."}
	// }
	loop {
	    if pes.atm.load(Ordering::Relaxed) == ProcedureExecutionStateEnum::Running {
		println!("============== Running move_to_up ");
		//let ret = move_to_jar( pi, step.jar_number, Some(&pes) );
		pi.green_light.set_low().expect("Couldn't turn green light off");
		pi.red_light.set_high().expect("Couldn't turn red light back on");
		let ret = move_to_up_position( pi, Some(&pes), false);
		if ret == MoveResult::MovedFullDistance {
		    break;
		}
	    }
	    if pes.atm.load(Ordering::Relaxed) == ProcedureExecutionStateEnum::Paused {
		pi.green_light.set_high().expect("Couldn't turn green light on");
		pi.red_light.set_low().expect("Couldn't turn red light off");
		if bool::from(pi.green_button.read_value().unwrap()) {
		    pes.atm.store(ProcedureExecutionStateEnum::Running, Ordering::Relaxed);
		    pi.green_light.set_low().expect("Couldn't turn green light off");
		    pi.red_light.set_high().expect("Couldn't turn red light back on");
		}
	    }
	    thread::sleep(time::Duration::from_millis(10));
	}
	
	pi.run_status = None;
	pi.red_light.set_low().expect("Couldn't turn off estop light.");

	pes.atm.store(ProcedureExecutionStateEnum::Completed, Ordering::Relaxed);
	let inc_result : FieldResult<Procedure> = increment_run_count(&mut proc);
	if inc_result.is_ok() {
	    pi.current_procedure = Some(inc_result.unwrap());
	}
    }
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


#[post("/move_to_pos/<axis>/<inches>")]
fn move_to_pos_handler(pi_state: State<SharedPi>, pes: State<ProcedureExecutionState>, axis: AxisDirection, inches: Inch) -> String {
    let pi_mutex = &mut pi_state.inner();
    let pi = &mut *pi_mutex.lock().unwrap();
    let pes = pes.inner();
    let ret = move_to_pos(pi, axis, inches, Some(pes), false);
    format! {"{:?}",ret}
}

#[post("/move_to_up_position")]
fn move_to_up_position_handler(pi_state: State<SharedPi>, pes: State<ProcedureExecutionState>) -> String {
    let pi_mutex = &mut pi_state.inner();
    let pi = &mut *pi_mutex.lock().unwrap();
    let pes = pes.inner();
    let ret = move_to_up_position(pi, Some(pes), true); // skip_soft_estop_check is set to true so that the user can manually raise the rack while paused
    format! {"{:?}",ret}
}

#[post("/move_to_down_position")]
fn move_to_down_position_handler(pi_state: State<SharedPi>, pes: State<ProcedureExecutionState>) -> String {
    let pi_mutex = &mut pi_state.inner();
    let pi = &mut *pi_mutex.lock().unwrap();
    let pes = pes.inner();
    let ret = move_to_down_position(pi, Some(pes), true); // skip_soft_estop_check is set to true so that the user can manually lower the rack while paused
    format! {"{:?}",ret}
}

#[post("/move_to_left_position")]
fn move_to_left_position_handler(pi_state: State<SharedPi>, pes: State<ProcedureExecutionState>) -> String {
    let pi_mutex = &mut pi_state.inner();
    let pi = &mut *pi_mutex.lock().unwrap();
    let pes = pes.inner();
    let ret = move_to_left_position(pi, Some(pes));
    format! {"{:?}",ret}
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
