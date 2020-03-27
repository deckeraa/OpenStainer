pub use crate::structs_and_consts::*;

use juniper::FieldResult;
use juniper::graphql_value;

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

