pub use crate::structs_and_consts::*;
pub use crate::motion::*;
pub use crate::couchdb::*;

use juniper::{FieldResult};
use juniper::graphql_value;
use rocket::State;

pub type Schema = juniper::RootNode<'static, Query, Mutation>;

pub struct Query;
#[juniper::object(Context = SharedPi)]
impl Query {
    fn apiVersion() -> &'static str {
        "1.0"
    }

    fn settings() -> FieldResult<Settings> {
	let settings = Settings { developer: true };
	println!("Returning settings: {:?}",settings);
	Ok(settings)
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
	let resp = reqwest::blocking::get(reqwest::Url::parse(format!("{}/_design/procedures/_view/procedures?include_docs=true",COUCHDB_URL).as_str()).unwrap());
	if resp.is_ok() {
	    let view_result = resp.unwrap().json::<ViewResult<Procedure>>().unwrap();
	    let v : Vec<Procedure> = view_result.rows.into_iter().map(|row| row.doc ).collect();
	    println!("Returning procedures: {:?}",v);
	    return Ok(v);
	}
	return Ok(vec![]); // TODO probably something better to do than return an empty array
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
}

#[rocket::post("/graphql", data = "<request>")]
pub fn post_graphql_handler(
    pi_state: State<SharedPi>,
    request: juniper_rocket::GraphQLRequest,
    schema: State<Schema>,
) -> juniper_rocket::GraphQLResponse {
    println!("GraphQLRequest {:?}",request.operation_names());
    request.execute(&schema, &pi_state)
}

#[get("/graphiql")]
pub fn graphiql() -> rocket::response::content::Html<String> {
    juniper_rocket::graphiql_source("/graphql")
}
