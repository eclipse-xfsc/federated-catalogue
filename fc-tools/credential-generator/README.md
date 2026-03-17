# Credential Generator

> **Note:** This tool was originally called "SD Generator" (for Gaia-X Self-Descriptions). The API now uses "Asset" for storage and "Credential" for verifiable credentials.

## Getting started

The tool can be used to generate (unsigned!) verifiable credential files. Make sure you have Python installed in your env.
The tool takes 3 input parameters:

    1. claimNr: Number of claims per credential
    2. credentialNr: Number of created credentials
    3. schema: What type of credential (service or legalperson)

The following example command creates two service credentials with 10 claims each:
```
> python 01_12_createCredentialNoGraphKeyword.py -claimNr 10 -credentialNr 2 -schema service
```