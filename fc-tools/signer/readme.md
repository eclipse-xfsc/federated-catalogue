# Federated Catalog Signer

## Usage

To sign and validate SDs a private-public key pair is required.
To create a self-signed pair this command can be executed:

```
openssl req -x509 -newkey rsa:4096 -keyout prk.ss.pem -out cert.ss.pem -sha256 -days 365 -nodes
```


Afterwards the SDs can be signed and verified. To do this build the project as part of the overall FC project, then run the signer tool for concrete SD file:

```
java -jar fc-tools-signer-<project.version>-full.jar <param=value>
```
The following parameters should be specified:
```
- a/algo: signature algorithm, supported values are: `RS256`, `PS256`, `ES256K`, `ES256KCC`, `ES256KRR`, `BBSPLus`, `EdDSA`, `ES256`, `ES384`, `ES512`. Default value `EdDSA`
- m/vmethod: the VerificationMethod value to be used in the signed self-description Proof. Default value is `did:web:compliance.lab.gaia-x.eu`
- puk/public-key: path to the public key file
- prk/private-key: path to the private key file. Default value is `src/main/resources/prk.ss.pem`
- sd/self-description: path to self-descriptiopn file to be signed. Default value is `src/main/resources/vc.json`
- ssd/signed-description: path to signed self-description file. Default value is `<self-description path/name>.signed.<self-description ext>`
```
## Known Issues

The underlying signer library `ld-signatures-java` has a bug: at `sign` processing it adds the `https://w3id.org/security/suites/jws-2020/v1` address as URI to VP/VC context, which causes `IllegalArgumentException` 
on subsequent Json normalization steps

```
java.lang.IllegalArgumentException: Type class java.net.URI is not supported.
	at org.glassfish.json.MapUtil.handle(MapUtil.java:75)
	at org.glassfish.json.JsonArrayBuilderImpl.populate(JsonArrayBuilderImpl.java:328)
	at org.glassfish.json.JsonArrayBuilderImpl.<init>(JsonArrayBuilderImpl.java:56)
	at org.glassfish.json.MapUtil.handle(MapUtil.java:67)
	at org.glassfish.json.JsonObjectBuilderImpl.populate(JsonObjectBuilderImpl.java:178)
	at org.glassfish.json.JsonObjectBuilderImpl.<init>(JsonObjectBuilderImpl.java:52)
	at org.glassfish.json.JsonProviderImpl.createObjectBuilder(JsonProviderImpl.java:174)
	at jakarta.json.Json.createObjectBuilder(Json.java:303)
	at foundation.identity.jsonld.JsonLDObject.toJsonObject(JsonLDObject.java:341)
	at foundation.identity.jsonld.JsonLDObject.toDataset(JsonLDObject.java:292)
	at foundation.identity.jsonld.JsonLDObject.normalize(JsonLDObject.java:328)
	at info.weboftrust.ldsignatures.canonicalizer.URDNA2015Canonicalizer.canonicalize(URDNA2015Canonicalizer.java:41)
	at info.weboftrust.ldsignatures.verifier.LdVerifier.verify(LdVerifier.java:57)
	at eu.xfsc.fc.tools.signer.SDSigner.check(SDSigner.java:110)
	at eu.xfsc.fc.tools.signer.SDSigner.main(SDSigner.java:66)
    ....
```
to prevent the issue please add the `https://w3id.org/security/suites/jws-2020/v1` URI as string preliminary to your SD in VP/VC context

## Known Issues : Signatures 442 Error
**Tirgger of the error:** Pushing a signed SD to a deployed version of federated Catalogue using the `POST/self-descriptions` API 
**Raised Error: ** 442 error caused by the verification error `Signatures error; … does not match with proof.` 
**Error handling: ** 
1. The generated private key is not automatically used and should be copied to the [resources](https://gitlab.eclipse.org/eclipse/xfsc/cat/fc-service/-/tree/main/fc-tools/signer/src/main/resources?ref_type=heads) directory

2. Secondly, the public key for the generated private key needs to be inserted into the FC Service that you deployed and to which you are trying to push the signed SD
