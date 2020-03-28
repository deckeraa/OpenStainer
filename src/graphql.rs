pub use crate::structs_and_consts::*;
pub use crate::motion::*;
pub use crate::couchdb::*;

use juniper::{FieldResult};
use rocket::State;

pub type Schema = juniper::RootNode<'static, Query, Mutation>;

pub struct Query;
#[juniper::object(Context = SharedPi)]
impl Query {
    fn apiVersion() -> &'static str {
        "1.0"
    }

    fn settings() -> FieldResult<Settings> {
	crate::couchdb::settings()
    }

    fn axis(context: &SharedPi, id: AxisDirection) -> FieldResult<Axis> {
	println!("axis id: {:?}",id);
        let pi = &mut *context.lock().unwrap();
	let stepper = get_stepper( pi, &id );
	let position_inches = match stepper.pos {
             Some(v) => format!("{}", pulses_to_inches(v, &stepper)),
             None => "Not homed".to_string(),
 	};
	let axis = Axis {position_inches: position_inches};
	println!("Returning axis: {:?}",axis);
        
        Ok(axis)
    }

    fn procedures() -> FieldResult<Vec<Procedure>> {
	crate::couchdb::procedures()
    }

    fn procedure_by_id(id: String) -> FieldResult<Procedure> {
	procedure_by_id(id)
    }

    fn current_procedure(shared_pi: &SharedPi) -> FieldResult<Option<Procedure>> {
	let pi = &mut *shared_pi.lock().unwrap();
	Ok(pi.current_procedure.clone())
    }

    fn run_status(shared_pi: &SharedPi) -> FieldResult<Option<ProcedureRunStatus>> {
	let pi = &mut *shared_pi.lock().unwrap();
	println!("run_status: {:?}", pi.run_status);
	let run_status_opt = pi.run_status.clone();
	if run_status_opt.is_none() {
	    return Ok(None);
	}
	let run_status = run_status_opt.unwrap();
	Ok(Some(run_status))
    }
}

pub struct Mutation;
#[juniper::object(Context = SharedPi)]
impl Mutation {
    fn save_procedure(procedure: ProcedureInputObject) -> FieldResult<Procedure> {
	crate::couchdb::save_procedure_input_object(procedure)
    }

    fn delete_procedure(id: String, rev: String) -> FieldResult<Vec<Procedure>> {
	crate::couchdb::delete_procedure(id,rev)
    }

    fn save_settings(settings: SettingsInputObject) -> FieldResult<Settings> {
	crate::couchdb::save_settings(settings)
    }
}

#[rocket::post("/graphql", data = "<request>")]
pub fn post_graphql_handler(
    pi_state: State<SharedPi>,
    request: juniper_rocket::GraphQLRequest,
    schema: State<Schema>,
) -> juniper_rocket::GraphQLResponse {
    request.execute(&schema, &pi_state)
}

#[get("/graphiql")]
pub fn graphiql() -> rocket::response::content::Html<String> {
    juniper_rocket::graphiql_source("/graphql")
}
