pub use crate::structs_and_consts::*;
pub use crate::couchdb::*;

use gpio::{GpioIn, GpioOut};
use std::{thread, time};
use thread_priority::*;
use std::sync::atomic::Ordering;
use std::convert::TryFrom;
use std::convert::TryInto;

pub fn inches_to_pulses(inches: Inch, stepper: &Stepper) -> PulseCount {
    (stepper.pulses_per_revolution as f64 * inches / stepper.travel_distance_per_turn) as u64
}

pub fn pulses_to_inches(pulses: PulseCount, stepper: &Stepper) -> Inch {
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

fn generate_wait_times_two(
    size: u64,
    band_inc: u64,
    max_hz: u64,
) -> Vec<std::time::Duration> {
    let mut times = vec![time::Duration::from_nanos(0); size.try_into().unwrap()];
    if size == 0 {
        return times;
    }

    let halfway = size / 2;
    for i in 0..halfway + 1 {
//        let hz = std::cmp::min(start_hz + slope * i, max_hz);
	let hz = std::cmp::min((i/800 + 1)*band_inc, max_hz);
        times[usize::try_from(i).unwrap()] = time::Duration::from_nanos(1_000_000_000 / hz);
    }
    for i in halfway..size {
        times[usize::try_from(i).unwrap()] = times[usize::try_from(size - i - 1).unwrap()];
    }
    times
}

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

fn generate_linear_ramp(start_hz: f64, max_hz: f64, ramp_length_in_turns: f64, number_of_turns: u64) -> Vec<std::time::Duration> {
    // let start_hz = start_hz.round() as u64;
    // let max_hz = max_hz.round() as u64;
    let turns_per_rev = 4000;
    let size = number_of_turns * turns_per_rev;

    let mut times = vec![time::Duration::from_nanos(0); size.try_into().unwrap()];
    let slope = (max_hz - start_hz) as f64 / (ramp_length_in_turns * turns_per_rev as f64) as f64;
    for i in 0..size {
	let hz = std::cmp::min((start_hz + (i as f64 *slope)) as u64 , max_hz as u64);
	times[usize::try_from(i).unwrap()] = time::Duration::from_nanos(1_000_000_000 / hz);
    }
    // let halfway = size / 2;
    // for i in 0..halfway + 1 {
    //     let hz = std::cmp::min(start_hz + slope * i, max_hz);
    //     times[usize::try_from(i).unwrap()] = time::Duration::from_nanos(1_000_000_000 / hz);
    // }
    // for i in halfway..size {
    //     times[usize::try_from(i).unwrap()] = times[usize::try_from(size - i - 1).unwrap()];
    // }
    times
}

fn generate_wait_times_three(
    size: u64,
    a: f64,
    max_hz: f64,
) -> Vec<std::time::Duration> {
    let size = size + 1; // we generate one more tmie than we need for an intermediate array, and then
    // put it in an array that's the right size.
    let mut times = vec![time::Duration::from_nanos(0); size.try_into().unwrap()];
    println!("generate_wait_times_three. size {} a {} max_hz {}",size,a,max_hz);

    let halfway = size / 2;
    for i in 1..halfway + 2 {
	//let calculated_time : f64 = (i as f64).sqrt() / (accel_in_hz_per_ns*steps_per_turn as f64).sqrt();
	let calculated_time : f64 = (1_000_000_000.0/2.0) * (2.0/1.0 as f64).sqrt() * (i as f64 / a).sqrt();
	// println!("calculated_time: {:?} ({:?}), sqrt(i): {:?}",
	// 	 time::Duration::from_nanos(calculated_time as u64),
	// 	 calculated_time,
	// 	 (i as f64).sqrt())
//	    ;
	let min_time_ns : f64 = 1_000_000_000 as f64 / max_hz as f64;
//	println!("min_time_ns {:?}",min_time_ns);
	let mut selected_time_ns : f64 = calculated_time;
	if min_time_ns > selected_time_ns {
	    selected_time_ns = min_time_ns;
	}
//	println!("selected time: {:?}", selected_time_ns);
        times[usize::try_from(i).unwrap() - 1] = time::Duration::from_nanos(selected_time_ns.round() as u64);
    }
    for i in halfway+1..size {
        times[usize::try_from(i).unwrap()] = times[usize::try_from(size - i).unwrap()];
    }
//    println!("times: {:?}", times);

    // now go through and calculation the actual wait time: (t_n+1 - t_n)/2
    // the 2 is in there because each pulse is divided equally into time with the signal HIGH and LOW.
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

#[test]
fn test_wait_times() {
    let expected = vec![time::Duration::from_nanos(1234)];
    assert_eq!(generate_wait_times_three(50,20000.0,10000.0), expected);
}
    
    

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

pub fn move_turns_at_freq(pi: &mut Pi, axis: AxisDirection, forward: bool, note_hz: f64, turns: u64) -> String {
    play_note(pi, axis, forward, note_hz, 0, Some(turns * 12800));
    "Moved one turn".to_string()
}

pub fn run_motor_test(pi: &mut Pi, axis: AxisDirection, forward: bool, accel_in_hz_per_sec: f64, max_hz: f64, number_of_turns: u64) -> String {
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

    //let times = generate_linear_ramp(start_hz, max_hz, ramp_length_in_turns, number_of_turns );
    let times = generate_wait_times_three(number_of_turns*4000, accel_in_hz_per_sec, max_hz);
    //println!("times: {:?}", times);
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

    // Enable and set the direction
    stepper.ena.set_low().expect("Couldn't turn on ena"); // logic is reversed to due transistor
    thread::sleep(time::Duration::from_millis(1));
    stepper.dir.set_value(forward).expect("Couldn't set dir");
    thread::sleep(time::Duration::from_millis(1));

//    let times = generate_wait_times(pulses, 1, 5, 17000);
    let times = generate_wait_times_two(pulses, 400, 17000);
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
    let ret_one = move_steps(pi, AxisDirection::Z, true, 160000, true, opt_pes, false);
    println!("Result of first home move {:?}",ret_one);
    if ret_one == MoveResult::HitEStop || ret_one == MoveResult::FailedToHome{
	return MoveResult::FailedToHome;
    }
    let ret_two = move_steps(pi, AxisDirection::X, false, 320000, true, opt_pes, false);
    if ret_one == MoveResult::HitLimitSwitch && ret_two == MoveResult::HitLimitSwitch {
        move_to_up_position(pi, opt_pes, false);
        move_to_left_position(pi, opt_pes);
    }
    ret_two
}

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
    let ret = move_to_down_position(pi, opt_pes, false);
    ret
}
