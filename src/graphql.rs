pub use crate::structs_and_consts::*;

use juniper::{FieldResult};
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
