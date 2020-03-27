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
	// match pi.current_procedure {
        //      Some(v) => Ok(v),
        //      None => Err(juniper::FieldError::new(format!("No current procedure"),graphql_value!({ "internal_error": "Couldn't parse response from CouchDB"})));
 	// }
    }

    fn run_status(shared_pi: &SharedPi) -> FieldResult<Option<ProcedureRunStatus>> {
	let pi = &mut *shared_pi.lock().unwrap();
	println!("run_status: {:?}", pi.run_status);
	let run_status_opt = pi.run_status.clone();
	if run_status_opt.is_none() {
	    return Ok(None);
	}
	let run_status = run_status_opt.unwrap();
	//run_status.run_state = ProcedureExecutionStateEnum::Paused;
	Ok(Some(run_status))
    }
}

pub struct Mutation;
#[juniper::object(Context = SharedPi)]
impl Mutation {
    fn save_procedure(procedure: ProcedureInputObject) -> FieldResult<Procedure> {
	let client = reqwest::blocking::Client::new();
	let body = serde_json::to_string(&procedure);
	if body.is_err() {
	    return Err(juniper::FieldError::new("Unable to parse the input object.",
						graphql_value!({ "internal_error": "Unable to parse the input object."})));
	}
	let body = body.unwrap();
	println!("save_procedure body: {:?}",body);
	let resp = client.post(COUCHDB_URL)//"http://localhost:5984/slide_stainer/")
	//.body(body) // .body(body)
	    .json(&procedure)
	    .send();
	if resp.is_err() {
	    return Err(juniper::FieldError::new("Unable to connect with CouchDB.",
						graphql_value!({ "internal_error": "Unable to connect with CouchDB."})));
	}
	//let resp = resp.unwrap();
	println!("save_procedure resp: {:?}",resp);
	let unwrapped = resp.unwrap();
//	println!("save_procedure resp.text(): {:?}",unwrapped.text());
	let parse_result = unwrapped.json::<CouchDBPOSTResponse>();//resp.unwrap().json::<Procedure>();
	//let parse_result = Err("hard-coded");
	if parse_result.is_err() {
//	    return Err(juniper::FieldError::new("Couldn't parse response from CouchDB",graphql_value!({ "internal_error": "Couldn't parse response from CouchDB"})));
	    return Err(juniper::FieldError::new(format!("Couldn't parse response from CouchDB: {:?}",parse_result.err()),graphql_value!({ "internal_error": "Couldn't parse response from CouchDB"})));
	}

	procedure_by_id(parse_result.unwrap().id)
    }

    fn delete_procedure(id: String, rev: String) -> FieldResult<Vec<Procedure>> {
	let client = reqwest::blocking::Client::new();
	let url : &str = &format!("{}/{}?rev={}",COUCHDB_URL,id,rev).to_string();
	let resp = client.delete(url).send();

	if resp.is_err() {
	    return Err(juniper::FieldError::new(format!("Unable to communicate with CouchDB server"),graphql_value!({"internal_error":"Unable to communicate with CouchDB server"})));
	}
	let resp = resp.unwrap();
	if resp.status() != 200 {
	    return Err(juniper::FieldError::new(format!("Recieved status {} and text {:?} from CouchDB when attempting to delete.",resp.status(),resp.text()),graphql_value!({"internal_error":"Unable to delete from CouchDB"})));
	}
	println!("delete_procedure text: {:?}", resp.text());
	
	let resp = reqwest::blocking::get(reqwest::Url::parse(format!("{}/_design/procedures/_view/procedures?include_docs=true",COUCHDB_URL).as_str()).unwrap());
	if resp.is_ok() {
	    let view_result = resp.unwrap().json::<ViewResult<Procedure>>().unwrap();
	    let v : Vec<Procedure> = view_result.rows.into_iter().map(|row| row.doc ).collect();
	    println!("Returning procedures: {:?}",v);
	    return Ok(v);
	}
	return Ok(vec![]); // TODO probably something better to do than return an empty array
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
