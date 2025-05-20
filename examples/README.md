Please see https://gitlab.com/gaia-x/data-infrastructure-federation-services/cat/fc-service/-/tree/main/fc-service-core/src/main/resources/defaultschema/shacl for further examples of SHACL shapes.

The examples in the `queries` subdirectory can be used as input for the sample queries documented in the [appendix of the Architecture Document](https://gaia-x.gitlab.io/data-infrastructure-federation-services/cat/architecture-document/architecture/catalogue-architecture.html#section-appendix).

### Add gx prefix to the catalog
- load the postmann collection from `/fc-service/fc-tools/Federated Catalogue API.postman_collection.json`
- POST `/fc-service/examples/gx.rdf` to /schema endpoint


### Add additional context(s) to the catalog
- open the `/fc-service-core/src/test/resources/application.yml` for editing
- search for `federated-catalogue.verification.additional-contexts`
- add a comma separated list of context URLs (e.g. `"https://registry.lab.gaia-x.eu/development/api/trusted-shape-registry/v1/shapes/jsonld/trustframework# , https://registry.lab.gaia-x.eu/v1/api/trusted-shape-registry/v1/shapes/jsonld/trustframework#"`
- build and run the catalog again