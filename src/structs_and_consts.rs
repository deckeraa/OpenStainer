use serde::*;
use atomic_enum::*;
use std::sync::atomic::*;
use std::sync::Mutex;
use std::fmt;
use rocket::request::FromParam;
use rocket::http::RawStr;

pub type Inch = f64;
pub type PulseCount = u64;

pub const LEFT_POSITION: Inch = 0.35;
pub const UP_POSITION: Inch = 3.5;
pub const JAR_SPACING: Inch = 1.9;

pub const COUCHDB_URL: &'static str = "http://localhost:5984/slide_stainer";


pub struct Stepper {
    pub ena: gpio::sysfs::SysFsGpioOutput,
    pub dir: gpio::sysfs::SysFsGpioOutput,
    pub pul: gpio::sysfs::SysFsGpioOutput,
    pub limit_switch_low: Option<gpio::sysfs::SysFsGpioInput>,
    pub limit_switch_high: Option<gpio::sysfs::SysFsGpioInput>,
    pub pos: Option<PulseCount>,
    pub position_limit: Inch,
    pub pulses_per_revolution: u64,
    pub travel_distance_per_turn: Inch,
}

#[atomic_enum]
#[derive(PartialEq,juniper::GraphQLEnum, Serialize, Deserialize)]
pub enum ProcedureExecutionStateEnum {
    NotStarted,
    Paused,
    Stopped,
    Running,
    Completed,
}

#[derive(Debug)]
pub struct ProcedureExecutionState {
    pub atm: AtomicProcedureExecutionStateEnum,
    pub seconds_remaining: AtomicU64,
}

#[derive(juniper::GraphQLObject, Debug, Serialize, Deserialize, Clone)]
#[graphql(description="A single step in a staining procedure.")]
pub struct ProcedureStep {
    #[graphql(description="The substance contained in the jar.")]
    pub substance: String,
    #[graphql(description="The time (in seconds) to immerse the slide in the staining jar.")]
    pub time_in_seconds: i32,
    #[graphql(description="The one-indexed jar number in which the slide is to be immersed.")]
    pub jar_number: i32,
}

#[derive(juniper::GraphQLInputObject, Debug, Serialize, Deserialize, Clone)]
#[graphql(description="A single step in a staining procedure.")]
pub struct ProcedureStepInputObject {
    #[graphql(description="The substance contained in the jar.")]
    pub substance: String,
    #[graphql(description="The time (in seconds) to immerse the slide in the staining jar.")]
    pub time_in_seconds: i32,
    #[graphql(description="The one-indexed jar number in which the slide is to be immersed.")]
    pub jar_number: i32,
}

impl From<ProcedureStep> for ProcedureStepInputObject {

    fn from(proc: ProcedureStep) -> Self {
	ProcedureStepInputObject {
	    substance : proc.substance,
	    time_in_seconds: proc.time_in_seconds,
	    jar_number: proc.jar_number,
	}
    }
}

#[derive(juniper::GraphQLObject, Debug, Serialize, Deserialize, Clone)]
#[graphql(description="A staining procedure")]
pub struct Procedure {
    #[serde(rename="_id")]
    #[graphql(name="_id", description="The _id of the procedure.")]
    pub id: String,
    
    #[graphql(name="_rev", description="The CouchDB _rev of the procedure.")]
    #[serde(rename="_rev")]
    pub rev: String,
    
    #[graphql(name="type", description="The CouchDB type of the procedure. Will always be :procedure.")]
    #[serde(rename="type")]
    pub type_: String,
    
    #[graphql(description="Name of the procedure.")]
    pub name: String,
    
    #[graphql(description="List of contents of what substanc is in jar")]
    pub jar_contents: Vec<String>,

    #[graphql(description="A list of steps in the staining procedure.")]
    pub procedure_steps: Vec<ProcedureStep>,
    
    #[graphql(description="Number of times to repeat a given procedure for a single run.")]
    #[serde(skip_serializing_if = "Option::is_none")]
    pub repeat: Option<i32>,

    #[graphql(description="Number of times this procedure has ever been run.")]
    #[serde(skip_serializing_if = "Option::is_none")]
    pub runs: Option<i32>,
}

#[derive(juniper::GraphQLInputObject, Debug, Serialize, Deserialize, Clone)]
#[graphql(description="A staining procedure")]
pub struct ProcedureInputObject {
    #[graphql(name="_id", description="The CouchDB _id of the procedure.")]
    #[serde(rename="_id", skip_serializing_if = "Option::is_none")]
    pub id: Option<String>,
    
    #[graphql(name="_rev", description="The CouchDB _rev of the procedure.")]
    #[serde(rename="_rev", skip_serializing_if = "Option::is_none")]
    pub rev: Option<String>,
    
    #[graphql(name="type", description="The CouchDB type of the procedure. Will always be :procedure.")]
    #[serde(rename="type")]
    pub type_: String,
    
    #[graphql(description="Name of the procedure.")]
    pub name: String,
    
    #[graphql(description="List of contents of what substanc is in jar")]
    pub jar_contents: Vec<String>,

    #[graphql(description="A list of steps in the staining procedure.")]
    pub procedure_steps: Vec<ProcedureStepInputObject>,
    
    #[graphql(description="Number of times to repeat a given procedure for a single run.")]
    #[serde(skip_serializing_if = "Option::is_none")]
    pub repeat: Option<i32>,

    #[graphql(description="Number of times this procedure has ever been run.")]
    #[serde(skip_serializing_if = "Option::is_none")]
    pub runs: Option<i32>,
}

impl From<Procedure> for ProcedureInputObject {

    fn from(proc: Procedure) -> Self {
	let mut procedure_steps : Vec<ProcedureStepInputObject> = Vec::new();
	for step in proc.procedure_steps {
	    procedure_steps.push(ProcedureStepInputObject::from(step));
	}
	ProcedureInputObject {
	    id : Some(proc.id),
	    rev : Some(proc.rev),
	    type_ : proc.type_,
	    name : proc.name,
	    jar_contents : proc.jar_contents.clone(),
	    procedure_steps : procedure_steps,
	    repeat : proc.repeat,
	    runs : proc.runs,
	}
    }
}

#[derive(juniper::GraphQLObject, Debug, Serialize, Deserialize, Clone)]
pub struct ProcedureRunStatus {
    // TODO make sure I come back and decide if these can be nil and make sure the description is updated.
    // #[graphql(description="CouchDB ID of the currently running procedure. Nil if no procedure is running.")]
    // current_procedure_id : String,
    
    // #[graphql(description="Name of the currently running procedure. Nil if no procedure is running.")]
    // current_procedure_name : String,
    
    #[graphql(description="One-indexed number of the current procedure step in the currently running procedure. Nil if no procedure is running.")]
    pub current_procedure_step_number : i32,

    //#[serde(skip)]
    //current_procedure_step_start_instant: Instant,
    
    // #[graphql(description="Start time of the current procedure step in the currently running procedure. Nil if no procedure is running or if the slide holder is currently en route to a staining jar.")]
    // current_procedure_step_seconds_remaining: String,
    
    #[graphql(description="The cycle number, one-indexed, of how many times the procedure has been repeated in a single run.")]
    pub current_cycle_number: i32,

    pub run_state: ProcedureExecutionStateEnum,
}

pub struct Pi {
    pub stepper_x: Stepper,
    pub stepper_z: Stepper,
    pub estop: gpio::sysfs::SysFsGpioInput,
    pub green_button: gpio::sysfs::SysFsGpioInput,
    pub red_light: gpio::sysfs::SysFsGpioOutput,
    pub green_light: gpio::sysfs::SysFsGpioOutput,
    pub current_procedure: Option<Procedure>,
    pub run_status: Option<ProcedureRunStatus>,
}

pub type SharedPi = Mutex<Pi>;

#[derive(juniper::GraphQLObject, Debug)]
#[graphql(description="A axis of motion on the device.")]
pub struct Axis {
    pub position_inches: String,
}

#[derive(juniper::GraphQLObject, Debug)]
pub struct Settings {
    pub developer: bool,
}

#[derive(Debug, PartialEq, Eq)]
pub enum MoveResult {
    MovedFullDistance,
    HitLimitSwitch,
    HitEStop,
    FailedDueToNotHomed,
    FailedToHome,
}

#[derive(juniper::GraphQLEnum, Debug)]
pub enum AxisDirection {
    X,
    Z,
}

impl<'r> FromParam<'r> for AxisDirection {
    type Error = &'r RawStr;

    fn from_param(param: &'r RawStr) -> Result<Self, Self::Error> {
        match param.as_str() {
            "x" => Ok(AxisDirection::X),
            "z" => Ok(AxisDirection::Z),
            _ => Err(param),
        }
    }
}

impl fmt::Display for AxisDirection {
    fn fmt(&self, f: &mut fmt::Formatter<'_>) -> fmt::Result {
        match self {
            AxisDirection::X => write!(f, "x"),
            AxisDirection::Z => write!(f, "z"),
        }
    }
}

pub fn get_stepper<'a>(pi: &'a mut Pi, axis: &AxisDirection) -> &'a mut Stepper {
    match axis {
        AxisDirection::X => &mut pi.stepper_x,
        AxisDirection::Z => &mut pi.stepper_z,
    }
}
