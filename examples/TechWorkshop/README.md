# Workshop Structure

These instructions were originally prepared for the GXFS Tech Workshop on 15–16 March 2023.

0. Introductory Presentation
1. Exercises
2. Hackathon (with _your_ use cases)

# Exercises

## Querying the Catalogue
These example queries are designed to help you to understand how to query the Self-Descriptions (SDs) stored in the Catalogue using the [web UI](https://fc-server.gxfs.dev/query).

The results may vary depending on what SDs are in the shared demo instance of the Catalogue at the given point in time; however, most queries are designed to provide reasonable output regardless of what is in the Catalogue.

* The initial queries are useful for a first exploration of what's in the Catalogue.
* The further queries are useful for a close inspection of a concrete entity of interest.

### Query 1: Get all relationships and corresponding nodes
```
MATCH (m) -[relation]-> (n) RETURN m, relation, n
```

### Query 2: Get all SDs of a specific type
```
MATCH (n:ServiceOffering) RETURN n.uri
```
or
```
MATCH (n:LegalPerson) RETURN n.uri
```

### Query 3: Get all properties of a specific SD

Here we have to use two different queries since the mapping of the RDF data model behind JSON-LD to the openCypher query language differentiates between properties pointing towards another object (“object properties”) and properties pointing towards literals (strings, numbers, etc. – “datatype properties”).

1. Get all relationships (properties pointing to another object)
```
MATCH (lp:LegalPerson)-[p]->(s)
WHERE lp.uri = "https://w3id.org/gaia-x/gax-trust-framework#Provider2"
RETURN p,s
```

2.	Get all remaining properties
```
MATCH (lp:LegalPerson)
WHERE lp.uri = "https://w3id.org/gaia-x/gax-trust-framework#Provider2" 
RETURN properties(p)
```

In case you get an empty result, try a different value for `lp.uri`, e.g., an identifier that was returned by Query 2.

### Query 4: Get all properties used by SDs of a certain type

As was the case for Query 3, we again need to use two different queries to get all properties.

1. Get all relationships (properties pointing to another object)
```
MATCH (lp:LegalPerson)-[p]->() 
RETURN p
```
2. Get all remaining properties
```
MATCH (lp:LegalPerson) 
RETURN keys(lp)
```
### Query 5: Get all Service Offerings offered by a specific Participant
```
MATCH (so:ServiceOffering)-[:offeredBy]->(lp:LegalPerson)
WHERE lp.uri = "https://w3id.org/gaia-x/gax-trust-framework#Provider2" 
RETURN so.uri
```
### Query 6: Get all Participants that offer services with certain characteristics
```
MATCH (lp:LegalPerson)<-[:offeredBy]-(so:ServiceOffering)
WHERE "Infrastructure Service" in so.keyword
RETURN lp.uri 
```
### Query 7: Get the legal name, type and legal Address of all SDs that contain the property "legalAddress"
```
MATCH (m)-[:legalAddress]->(n) 
RETURN LABELS(m) as type, n as legalAddress, m.legalName as legalName
```
### Query 8: Return all nodes that contain the property "offeredBy"
```
MATCH (n)-[:offeredBy]->(m) 
RETURN n
```
### Query 9: Return all Service Offerings that have a specific name
```
MATCH (n:ServiceOffering) 
WHERE n.name = "Service Offering 1" 
RETURN n.uri
```
### Query 10: Return all Legal Persons that offer a data service, which depends on another object.
```
MATCH (lp:LegalPerson)<-[:offeredBy]-(so1:ServiceOffering)-[:dependsOn]->(so2)
WHERE "Data Service" in so1.keyword
RETURN lp.uri as Provider
```

## Exercises with Postman
The files used for the exercises can be found [here](https://gitlab.eclipse.org/eclipse/xfsc/cat/fc-service/-/tree/addTrustedCloudExample/examples/Trusted_Cloud_Examples).

These exercises make use of several functionalities of the Catalogue, giving the user an overview of what the Catalogue is capable of. For each of these exercises a video guiding through the exercise is provided [here](https://gitlab.com/gaia-x/data-infrastructure-federation-services/gxfs-workshop/-/tree/main/Catalogue/Basics).

Use the version of the Postman collection that's provided for the workshop [here](https://gitlab.com/gaia-x/data-infrastructure-federation-services/gxfs-workshop/-/blob/main/Catalogue/Basics/Test%20Stand.postman_collection.json).

For further reference, see [the OpenAPI documentation of the Catalogue's API](https://gitlab.eclipse.org/eclipse/xfsc/cat/fc-service/-/blob/main/openapi/fc_openapi.yaml).

One way to access the individual API endpoints is to have Postman generate an access token per endpoint as follows:
1. In the tree view of all endpoints of this collection, pick the one you would like to use.
2. Go to the “Authorization” tab.
3. Scroll to the “Configure New Token” form.  Enter the “Username” and “Password” provided during the workshop.
4. Press “Get New Access Token”.
5. Next, Postman will offer you to _use_ this token for all subsequent requests to this endpoint.

The following exercises modify the state of the database of the shared demo instance.  Thus, we need to proceed one group at a time and might need to “clean up” in between.  If in doubt, use the shared chat for coordination.

### Exercise 1: Add trusted-cloud Service Offering SD to the Catalogue

Step 1: Try validating `IonosCloud_ServiceOffering-instance_invalid.json` using the `POST /verification` endpoint in the Postman collection.

Result: The SD cannot be validated, since it is not a subclass of `gax-core:ServiceOffering`, i.e., the overall superclass of all Service Offerings in Gaia-X. This information is missing, because the Trusted Cloud ontology graph has not yet been uploaded to the Catalogue.

Step 2: Try loading the `trusted-cloud_generated.rdf` ontology graph to the Catalogue using the `POST /schemas` endpoint. Then repeat step 1.

Result: The SD validates successfully even though it should actually be invalid, since the Trusted Cloud shapes graph (i.e., the schema to be validated against) was not yet uploaded to the Catalogue.

Step 3: Try loading the `trusted-cloud-mergedShapesGraph.rdf` to the Catalogue using the `POST /schemas` endpoint. Then repeat step 1.

Result: SD does not validate successfully, since a mandatory property is missing.

Step 4: Try validating `IonosCloud_ServiceOffering-instance_valid.json` using the `POST /verification` endpoint. Here, the missing property was added.

Result: SD validates successfully.

Step 5: Try loading the invalid SD to the graph.

Result: No success.

Step 6: Try loading the valid SD to the graph.

Result: Success.

### Exercise 2: Add a Participant SD to the Catalogue, update the SD afterwards, then delete it

Before continuing with this task make sure that Step 2 of Exercise 1 has been successfully done.

Step 1: Try loading `Ionos_Provider-instance.json` to the Catalogue using the `POST /participants` endpoint.

Result: Success.

Step 2: Try updating the SD with the ID `"https%3A%2F%2Fexample.edu%2Fissuers%2F565049"` by loading `Ionos_Provider-instance_updated.json` to the Catalogue using the `PUT /participants/{id}` endpoint.  Make sure to set the right ID.

Result: SD successfully updated.

Step 3: Try deleting the SD with the ID `"https%3A%2F%2Fexample.edu%2Fissuers%2F565049"` by using the `DEL /participants/{id}` endpoint.  Make sure to set the right ID.

Result: SD successfully removed from the Catalogue.

Note: The ID of the SD has to be given in a URL-encoded way. To encode the ID, an [online encoder](https://www.urlencoder.org/) can be used.

### Exercise 3: Add a new Schema to the Catalogue, update it afterwards, then delete it

This exercise works analogously to exercise 2. But this time we go through the lifecycle of a schema instead of a participant.

Step 1: Try loading `test-schema.rdf` to the Catalogue using the `post /schemas` endpoint.

Result: Success. To check this, you can use the `GET /schemas` endpoint.

Step 2: Try updating the schema with the ID `"http%3A%2F%2Fw3id.org%2Fgaia-x%2Ftest-schema%23"` by loading `test-schema-updated.rdf` to the Catalogue using the `PUT /schemas/{id}` endpoint. Make sure to set the right ID.

Result: Schema successfully updated.

Step 3: Try deleting the schema with the ID `"http%3A%2F%2Fw3id.org%2Fgaia-x%2Ftest-schema%23"` by using the `DEL /schemas/{id}` endpoint. Make sure to set the right ID.

Result: Schema successfully removed from the Catalogue. To check this, you can use the `GET /schemas` endpoint.

Note: The ID of the schema has to be given in an URL-encoded way. To encode the ID an [online encoder](https://www.urlencoder.org/) can be used.

### Exercise 4: Query existing SDs in the Catalogue

All the queries shown above in the section "Querying the Catalogue" can also be executed in Postman. To do this we make use of the `POST /query MATCH` endpoint. Below is shown how to format the body for such an API call. Here, it is important to write the complete statement in one line. The example here uses the example "Query 6" from the "Query the Catalogue section". All other queries can be used in the same way.

```
{
"statement": "MATCH (lp:LegalPerson)<-[:offeredBy]-(so:ServiceOffering) WHERE \"Infrastructure Service\" in so.keyword RETURN lp.uri"
}
```

# Prerequisites

* Create a [Postman](https://www.postman.com/) account.  Ideally, install the Postman desktop client.
* Be able to run the ["Signer" tool from the respective GXFS / Catalogue repository](https://gitlab.com/gaia-x/data-infrastructure-federation-services/cat/fc-tools/signer/) (requires a recent Java environment).
