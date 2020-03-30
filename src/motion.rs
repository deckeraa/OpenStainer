pub use crate::structs_and_consts::*;
pub use crate::couchdb::*;

use gpio::{GpioIn, GpioOut};
use std::{thread, time};
use thread_priority::*;
use std::sync::atomic::Ordering;
use std::convert::TryFrom;
use std::convert::TryInto;

// Converts inches to pulses for a given stepper's configuration
pub fn inches_to_pulses(inches: Inch, stepper: &Stepper) -> PulseCount {
    (stepper.pulses_per_revolution as f64 * inches / stepper.travel_distance_per_turn) as u64
}

// Converts pulses to inches for a given stepper's configuration
pub fn pulses_to_inches(pulses: PulseCount, stepper: &Stepper) -> Inch {
    pulses as f64 * stepper.travel_distance_per_turn / stepper.pulses_per_revolution as f64
}

// Generates an array of wait times to play a note for a given duration.
// If step_override has a value other than None, then the number of steps passed will be used
// rather than duration_ms to determine how long to play the note.
// This is mostly useful for analyzing the potential loss of steps while turning a stepper at a fixed frequency.
fn generate_note_times(note_hz: f64, duration_ms: u64, step_override: Option<u64>) -> Vec<std::time::Duration> {
    println!("note_hz before round {:?}", note_hz);
    let note_hz = note_hz.round() as u64;
    println!("note_hz after round {:?}", note_hz);
    let size = match step_override {
	Some(s) => s,
	None => note_hz * duration_ms / 1000,
    };
    let mut times = vec![time::Duration::from_nanos(0); size.try_into().unwrap()];
    if size == 0 {
        return times;
    }
    for i in 0..size {
	times[usize::try_from(i).unwrap()] = time::Duration::from_nanos(1_000_000_000 / note_hz);
    }
    times
}

// Generates an array of wait times to be used to drive the stepper.
// Size is the number of steps to move the stepper, and a is a constant acceleration to apply.
// a is in Hz/sec BUT it has a hidden constant coefficient based upon the stepper driver settings.
// E.g. if you set the stepper driver to 4000 steps/rev, then a is going to need to be different than if
// it was set to 1600 steps/rev.
fn generate_wait_times(
    size: u64,
    a: f64,
) -> Vec<std::time::Duration> {
    let size = size + 1; // we generate one more tmie than we need for an intermediate array, and then
    // put it in an array that's the right size.
    let mut times = vec![time::Duration::from_nanos(0); size.try_into().unwrap()];

    // We fill up the array with times halfway through and then mirror it to produce the deceleration ramp.
    let halfway = size / 2;
    for i in 1..halfway + 2 {
	// If we want a constant acceleration, then given steps y, time t and acceleration a, acceleration is
	// (d^2y / dt^2) = a
	// Integrating gives us velocity:
	// dy/dt = at
	// (For our application we don't care about the +C term resulting from integration).
	// Integrating once again gives us:
	// y = (1/2)at^2
	// When driving a stepper, we have to provide a signal for each pulse.
	// Thus, we can't pick y values based on t, instead we need to find t for values y = 1,2,3,4,5,6 etc.
	// Sovled for t: t = sqrt(2y/a).
	// (We don't care about the negative root since negative t values are nonsensical for our application).
	let calculated_time : f64 = (1_000_000_000.0/2.0) * (2.0/1.0 as f64).sqrt() * (i as f64 / a).sqrt();
	let mut selected_time = time::Duration::from_nanos(calculated_time.round() as u64);
	if selected_time < time::Duration::from_micros(5) {
	    selected_time = time::Duration::from_micros(5); // minimum size of the signal supported by the stepper driver
	}
        times[usize::try_from(i).unwrap() - 1] = selected_time;
    }
    // Now mirror the array ot produce the deceleration ramp
    for i in halfway+1..size {
        times[usize::try_from(i).unwrap()] = times[usize::try_from(size - i).unwrap()];
    }

    // now go through and calculate the actual wait time: (t_n+1 - t_n)/2
    // The array above gives us absolute times at which point steps need to occur, this gives us the distance
    // between times for use in our sleep() function when in the actual motor driving loop.
    // The 2 is in there because each pulse is divided equally into time with the signal HIGH and LOW.
    let mut wait_times = vec![time::Duration::from_nanos(0); size.try_into().unwrap()];
    for i in 1..halfway + 1{
	let t_b = times[usize::try_from(i).unwrap()];
	let t_a = if i == 0 { time::Duration::from_nanos(0) } else { times[usize::try_from(i-1).unwrap()] };
	wait_times[usize::try_from(i).unwrap()] = (t_b - t_a) / 2;
    }
    for i in halfway+1..size {
         wait_times[usize::try_from(i).unwrap()] = wait_times[usize::try_from(size - i).unwrap()];
    }
    wait_times.remove(0); // the first element is always 0 s, (since we start at index 1) so taking that one out.
    return wait_times;
}

// Plays a note on a given stepper at a given frequency.
// If step_override has a value other than None, then the number of steps passed will be used
// rather than duration_ms to determine how long to play the note.
// This is mostly useful for analyzing the potential loss of steps while turning a stepper at a fixed frequency.
pub fn play_note(pi: &mut Pi, axis: AxisDirection, forward: bool, note_hz: f64, duration_ms: u64, step_override: Option<u64>) -> String {
    let stepper = match axis {
        AxisDirection::X => &mut pi.stepper_x,
        AxisDirection::Z => &mut pi.stepper_z,
    };

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
    stepper.dir.set_value(forward).expect("Couldn't set dir");
    thread::sleep(time::Duration::from_millis(1));
    stepper.ena.set_low().expect("Couldn't turn on ena"); // logic is reversed to due transistor
    thread::sleep(time::Duration::from_millis(1));

    let times = generate_note_times( note_hz, duration_ms, step_override );
    for t in times.iter() {
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
    
    "Finished playing note.".to_string()
}

// Moves a given number of turns at a certain frequency.
pub fn move_turns_at_freq(pi: &mut Pi, axis: AxisDirection, forward: bool, note_hz: f64, turns: u64) -> String {
    let pulses_per_revolution;
    {
	let stepper = match axis {
            AxisDirection::X => &mut pi.stepper_x,
            AxisDirection::Z => &mut pi.stepper_z,
	};
	pulses_per_revolution = stepper.pulses_per_revolution;
    }
    play_note(pi, axis, forward, note_hz, 0, Some(turns * pulses_per_revolution));
    "Moved one turn".to_string()
}

// Runs a test of a given stepper and acceleration constant.
pub fn run_motor_test(pi: &mut Pi, axis: AxisDirection, forward: bool, acceleration_constant: f64, number_of_turns: u64) -> String {
    let stepper = match axis {
        AxisDirection::X => &mut pi.stepper_x,
        AxisDirection::Z => &mut pi.stepper_z,
    };

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

    let times = generate_wait_times(number_of_turns*4000, acceleration_constant);
    for t in times.iter() {
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
    
    "Finished motor test.".to_string()
}

// Moves the stepper by a certain number of steps
pub fn move_steps(pi: &mut Pi, axis: AxisDirection, forward: bool, pulses: u64, is_homing: bool, opt_pes: Option<&ProcedureExecutionState>, skip_soft_estop_check: bool) -> MoveResult {
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

    // generate the pulses
    let times = generate_wait_times(pulses,64_000_000.0);

    // Enable and set the direction
    stepper.ena.set_low().expect("Couldn't turn on ena"); // logic is reversed to due transistor
    thread::sleep(time::Duration::from_millis(1));
    stepper.dir.set_value(forward).expect("Couldn't set dir");
    thread::sleep(time::Duration::from_millis(1));

    let mut hit_limit_switch = false;
    let mut hit_e_stop = false;

    let mut moved_pulses = 0;
    for t in times.iter() {
        moved_pulses = moved_pulses + 1;
        // check limit switch
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
	    if opt_pes.is_some() { 
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
            "Moved {} pulses, updating pi.stepper_x.pos to {:?}",
	    moved_pulses,
            Some(if forward { v + moved_pulses } else { v - moved_pulses })
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

// Moves to a position given in inches.
pub fn move_to_pos(pi: &mut Pi, axis: AxisDirection, inches: Inch, opt_pes: Option<&ProcedureExecutionState>, skip_soft_estop_check: bool) -> MoveResult {
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

pub fn move_to_up_position(pi: &mut Pi, opt_pes: Option<&ProcedureExecutionState>, skip_soft_estop_check: bool) -> MoveResult {
    move_to_pos(pi, AxisDirection::Z, UP_POSITION, opt_pes, skip_soft_estop_check)
}

pub fn move_to_down_position(pi: &mut Pi, opt_pes: Option<&ProcedureExecutionState>, skip_soft_estop_check: bool) -> MoveResult {
    move_to_pos(pi, AxisDirection::Z, 0.0, opt_pes, skip_soft_estop_check)
}

pub fn move_to_left_position(pi: &mut Pi, opt_pes: Option<&ProcedureExecutionState>) -> MoveResult {
    move_to_pos(pi, AxisDirection::X, LEFT_POSITION, opt_pes, false)
}

pub fn home(pi: &mut Pi, opt_pes: Option<&ProcedureExecutionState>) -> MoveResult {
    let ret_one = move_steps(pi, AxisDirection::Z, true, 160000*4, true, opt_pes, false);
    println!("Result of first home move {:?}",ret_one);
    if ret_one == MoveResult::HitEStop || ret_one == MoveResult::FailedToHome{
	return MoveResult::FailedToHome;
    }
    let ret_two = move_steps(pi, AxisDirection::X, false, 320000*4, true, opt_pes, false);
    if ret_one == MoveResult::HitLimitSwitch && ret_two == MoveResult::HitLimitSwitch {
        move_to_up_position(pi, opt_pes, false);
        move_to_left_position(pi, opt_pes);
    }
    ret_two
}

// This is used to determine whether or not move_to_jar needs to move to the up position and move over before
// moving down.
fn known_to_be_at_jar_position(pi: &mut Pi, jar_number: i32) -> bool {
    if pi.stepper_x.pos.is_none() {
	return false;
    }
    let current_inches : Inch = pulses_to_inches(pi.stepper_x.pos.unwrap(), &pi.stepper_x);
    let target_inches  : Inch = LEFT_POSITION + JAR_SPACING * (jar_number - 1) as f64;
    (target_inches - current_inches).abs() < 0.1
}

pub fn move_to_jar(pi: &mut Pi, jar_number: i32, opt_pes: Option<&ProcedureExecutionState>) -> MoveResult {
    if !known_to_be_at_jar_position(pi, jar_number) {
	let ret: MoveResult = move_to_up_position(pi, opt_pes, false);
	println!("Result of move_to_up_position {:?}", ret);
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
	println!("Result of move_to_pos {:?}", ret);
	if ret == MoveResult::HitLimitSwitch
            || ret == MoveResult::HitEStop
            || ret == MoveResult::FailedDueToNotHomed
	{
            return ret;
	}
    }
    let ret = move_to_down_position(pi, opt_pes, false);
    println!("Result of move_to_down_position {:?}", ret);
    ret
}
