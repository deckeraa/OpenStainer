pub use crate::structs_and_consts::*;

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

pub fn move_to_up_position(pi: &mut Pi, opt_pes: Option<&ProcedureExecutionState>) -> MoveResult {
    move_to_pos(pi, AxisDirection::Z, UP_POSITION, opt_pes, true)
}

pub fn move_to_down_position(pi: &mut Pi, opt_pes: Option<&ProcedureExecutionState>) -> MoveResult {
    move_to_pos(pi, AxisDirection::Z, 0.0, opt_pes, true)
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
        move_to_up_position(pi, opt_pes);
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
