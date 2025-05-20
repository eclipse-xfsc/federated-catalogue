# fc-tools

## Postman Collection

The Postman Collection helps to test the API of the Federated Catalogue more easily.

To make use of it, you need to configure the Collection variables first according to your specific environment. The variable
`access_token` and `refresh_token` don't need to be set manually, since they are filled automatically as described in the 
following section.

### Authorization

Before sending a request to the Federated Catalogue, you need to execute the Request `Authorization/Retrieve access token` 
which retrieves an access token from the configured Keycloak instance. The access token is automatically set
in the corresponding Collection variable which is referenced as a Bearer token for all requests within the Collection.

_Note: The Keycloak access tokens from the default deployment environment have only a short lifetime and need to be refreshed 
regularly._