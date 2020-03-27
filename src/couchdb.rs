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

// convenience function for generation juniper::FieldError
pub fn juniper_err<T>(message: String) -> FieldResult<T>{
    Err(juniper::FieldError::new(message.clone(),graphql_value!({ "internal_error": message})))
}

pub fn procedure_by_id(id: String) -> FieldResult<Procedure> {
    let url : &str = &format!("{}/{}",COUCHDB_URL,id).to_string();
    let resp = reqwest::blocking::get(url);
    if resp.is_ok() {
	let parse_result = resp.unwrap().json::<Procedure>();
	if parse_result.is_err() {
	    return juniper_err::<Procedure>(format!("Couldn't parse response from CouchDB: {:?}",parse_result.err()));
	}
	let proc = parse_result.unwrap();
	return Ok(proc);
    }
    return juniper_err::<Procedure>("No procedure with that ID found.".to_string());
}

pub fn save_procedure(procedure: Procedure) -> FieldResult<Procedure> {
    save_procedure_input_object( ProcedureInputObject::from(procedure) )
}

pub fn save_procedure_input_object(procedure: ProcedureInputObject) -> FieldResult<Procedure> {
    let client = reqwest::blocking::Client::new();
    let resp = client.post(COUCHDB_URL)
	.json(&procedure)
	.send();
    if resp.is_err() {
	return juniper_err::<Procedure>("Unable to connect with CouchDB.".to_string());
    }
    let unwrapped = resp.unwrap();
    let parse_result = unwrapped.json::<CouchDBPOSTResponse>();
    if parse_result.is_err() {
	return juniper_err::<Procedure>(format!("Couldn't parse response from CouchDB: {:?}",parse_result.err()));
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
}

pub fn delete_procedure(id: String, rev: String) -> FieldResult<Vec<Procedure>> {
    let client = reqwest::blocking::Client::new();
    let url : &str = &format!("{}/{}?rev={}",COUCHDB_URL,id,rev).to_string();
    let resp = client.delete(url).send();

    if resp.is_err() {
	return juniper_err::<Vec<Procedure>>(format!("Unable to communicate with CouchDB server"));
    }
    let resp = resp.unwrap();
    if resp.status() != 200 {
	return juniper_err::<Vec<Procedure>>(format!("Recieved status {} and text {:?} from CouchDB when attempting to delete.",resp.status(),resp.text()));
    }
    
    let resp = reqwest::blocking::get(reqwest::Url::parse(format!("{}/_design/procedures/_view/procedures?include_docs=true",COUCHDB_URL).as_str()).unwrap());
    if resp.is_ok() {
	let view_result = resp.unwrap().json::<ViewResult<Procedure>>().unwrap();
	let v : Vec<Procedure> = view_result.rows.into_iter().map(|row| row.doc ).collect();
	println!("Returning procedures: {:?}",v);
	return Ok(v);
    }
    return juniper_err::<Vec<Procedure>>("Unable to retrive list of procedures after delete.".to_string());
}
