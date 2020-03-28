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

// convenience function for generating juniper::FieldError
pub fn juniper_err<T>(message: String) -> FieldResult<T>{
    Err(juniper::FieldError::new(message.clone(),graphql_value!({ "internal_error": message})))
}

pub fn get_doc<T: serde::de::DeserializeOwned>(id: String) -> FieldResult<T> {
    let url : &str = &format!("{}/{}",COUCHDB_URL,id).to_string();
    let resp = reqwest::blocking::get(url);
    if resp.is_ok() {
	let parse_result = resp.unwrap().json::<T>();
	if parse_result.is_err() {
	    return juniper_err::<T>(format!("Couldn't parse response from CouchDB: {:?}",parse_result.err()));
	}
	let proc = parse_result.unwrap();
	return Ok(proc);
    }
    return juniper_err::<T>("No procedure with that ID found.".to_string());
}

pub fn procedure_by_id(id: String) -> FieldResult<Procedure> {
    get_doc::<Procedure>(id)
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

pub fn procedures() -> FieldResult<Vec<Procedure>> {
    let resp = reqwest::blocking::get(reqwest::Url::parse(format!("{}/_design/procedures/_view/procedures?include_docs=true",COUCHDB_URL).as_str()).unwrap());
    if resp.is_ok() {
	let view_result = resp.unwrap().json::<ViewResult<Procedure>>().unwrap();
	let v : Vec<Procedure> = view_result.rows.into_iter().map(|row| row.doc ).collect();
	return Ok(v);
    }
    return juniper_err::<Vec<Procedure>>("Unable to retrive list of procedures after delete.".to_string());
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
    
    return procedures();
}

pub fn settings() -> FieldResult<Settings> {
    get_doc::<Settings>("settings".to_string())
}

pub fn save_settings(settings_input_object: SettingsInputObject) -> FieldResult<Settings> {
    let client = reqwest::blocking::Client::new();
    let resp = client.post(COUCHDB_URL)
	.json(&settings_input_object)
	.send();
    if resp.is_err() {
	return juniper_err::<Settings>("Unable to connect with CouchDB.".to_string());
    }
    let unwrapped = resp.unwrap();
    let parse_result = unwrapped.json::<CouchDBPOSTResponse>();
    if parse_result.is_err() {
	return juniper_err::<Settings>(format!("Couldn't parse response from CouchDB: {:?}",parse_result.err()));
    }

    settings()
}

#[derive(Debug, Serialize, Deserialize, Clone)]
struct ViewsProcedures {
    map: String
}

#[derive(Debug, Serialize, Deserialize, Clone)]
struct DesignDocumentViews {
    procedures: ViewsProcedures
}

#[derive(Debug, Serialize, Deserialize, Clone)]
struct DesignDocument {
    #[serde(rename="_id")]
    id: Option<String>,
    
    #[serde(rename="_rev")]
    rev: Option<String>,
    
    views: DesignDocumentViews,
    language: String,
}

pub fn install_views() -> String {
    // TODO this is a one-time install. Once the document exists, this won't update the doc since it doesn't have
    // the _id and _rev attributes from the existing doc on the db.
    // However, this suffices for the present need of storing the view functions in source-controlled code.
    let doc = DesignDocument {
	id: None,
	rev: None,
	views: DesignDocumentViews {
	    procedures: ViewsProcedures {
		map: "function (doc) { if(doc.type == \'procedure\') { emit(doc._id, doc.name); } }".to_string(),
	    }
	},
	language: "javascript".to_string(),
    };
    
    
    let client = reqwest::blocking::Client::new();
    let resp = client.post(COUCHDB_URL)
	.json(&doc)
	.send();
    return format!("{:?}",resp);
}

