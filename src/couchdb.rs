pub use crate::structs_and_consts::*;

use juniper::FieldResult;
use juniper::graphql_value;
use serde::*;

#[derive(Debug, Serialize, Deserialize)]
pub struct SingleViewResultWithIncludeDocs<T> {
    pub id: String,
    pub key: String,
    pub doc: T,
}

#[derive(Debug, Serialize, Deserialize)]
pub struct ViewResult<T> {
    pub total_rows: i64,
    pub offset: i64,
    pub rows: Vec<SingleViewResultWithIncludeDocs<T>>,
}

#[derive(Debug, Serialize, Deserialize)]
pub struct CouchDBPOSTResponse {
    pub ok: bool,
    pub id: String,
    pub rev: String,
}

// This code lives outside of Query since the #[juniper::object(Context = SharedPi)] is preventing me from
// referencing Query::procedure_by_id elsewhere in code.
pub fn procedure_by_id(id: String) -> FieldResult<Procedure> {
    let url : &str = &format!("{}/{}",COUCHDB_URL,id).to_string();
    let resp = reqwest::blocking::get(url);
    if resp.is_ok() {
	let parse_result = resp.unwrap().json::<Procedure>();
	if parse_result.is_err() {
	    return Err(juniper::FieldError::new(format!("Couldn't parse response from CouchDB: {:?}",parse_result.err()),graphql_value!({ "internal_error": "Couldn't parse response from CouchDB"})));
	}
	let proc = parse_result.unwrap();
	return Ok(proc);
    }
    return Err(juniper::FieldError::new("No procedure with that ID found.",
					graphql_value!({ "internal_error": "No procedure with that ID found."})))
}

fn save_procedure(procedure: Procedure) -> FieldResult<Procedure> {
    save_procedure_input_object( ProcedureInputObject::from(procedure) )
}

fn save_procedure_input_object(procedure: ProcedureInputObject) -> FieldResult<Procedure> {
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

pub fn increment_run_count( proc: &mut Procedure) -> FieldResult<Procedure> {
    // increment the number of runs
    let cur_runs = match proc.runs {
	Some(v) => v,
	None => 0
    };
    proc.runs = Some(cur_runs+1);

    // save the updated procedure
    return save_procedure(proc.clone());

    // TODO
    //return Err(juniper::FieldError::new(format!("TODO"),graphql_value!({ "internal_error": "Couldn't parse response from CouchDB"})));
}
