# Federated Catalog Credential Signer

> **Note:** This tool signs credentials using Tagus-era JsonWebSignature2020 Linked Data proofs.
> LD proof verification has been removed from the Federated Catalogue (Loire release).
> The catalogue now only accepts JWT and Enveloped Credential (EVC/EVP) signatures.
> This tool needs to be rewritten to produce JWT signatures or removed entirely.

## Usage

To sign and validate credentials a private-public key pair is required.
To create a self-signed pair this command can be executed:

```
openssl req -x509 -newkey rsa:4096 -keyout prk.ss.pem -out cert.ss.pem -sha256 -days 365 -nodes
```


Afterwards the credentials can be signed and verified. To do this build the project as part of the overall FC project, then run the signer tool for a concrete credential file:

```
java -jar fc-tools-signer-<project.version>-full.jar <param=value>
```
The following parameters should be specified:
```
- a/algo: signature algorithm, supported values are: `RS256`, `PS256`, `ES256K`, `ES256KCC`, `ES256KRR`, `BBSPLus`, `EdDSA`, `ES256`, `ES384`, `ES512`. Default value `EdDSA`
- m/vmethod: the VerificationMethod value to be used in the signed credential Proof. Default value is `did:web:compliance.lab.gaia-x.eu`
- puk/public-key: path to the public key file
- prk/private-key: path to the private key file. Default value is `src/main/resources/prk.ss.pem`
- sd/self-description/credential: path to credential file to be signed. Default value is `src/main/resources/vc.json`
- ssd/signed-description/signed-credential: path to signed credential file. Default value is `<credential path/name>.signed.<credential ext>`
```
## Known Issues

The former signer library `ld-signatures-java` had a bug where `sign` processing added `https://w3id.org/security/suites/jws-2020/v1` as a URI to VP/VC context, causing `IllegalArgumentException` on JSON normalization. The library was replaced with `data-integrity-java` — it is unknown whether this bug persists in the new library.

As a workaround, the fixture files in this module pre-include the URI as a string in the VP/VC context.

> **Note:** These issues become moot once the tool is rewritten to produce JWT signatures (see deprecation note above).

## Known Issues : Signatures 442 Error
**Trigger of the error:** Pushing a signed credential to a deployed version of Federated Catalogue using the `POST /assets` API
**Raised Error:** 442 error caused by the verification error `Signatures error; … does not match with proof.`
**Error handling:**
1. The generated private key is not automatically used and should be copied to the [resources](https://gitlab.eclipse.org/eclipse/xfsc/cat/fc-service/-/tree/main/fc-tools/signer/src/main/resources?ref_type=heads) directory

2. Secondly, the public key for the generated private key needs to be inserted into the FC Service that you deployed and to which you are trying to push the signed credential
