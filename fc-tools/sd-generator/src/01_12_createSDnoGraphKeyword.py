import argparse

def createServiceOfferingSD(attrNumber, sdNumber):
    for i in range(1, sdNumber + 1):
        vp = "{\n\t\"@context\": [\"https://www.w3.org/2018/credentials/v1\"],\n\t\"@id\": \"http://example.edu/verifiablePresentation/self-description%s\",\n\t\"type\": [\"VerifiablePresentation\"],\n\t\"verifiableCredential\": {\n\t\t\"@context\": [\"https://www.w3.org/2018/credentials/v1\"],\n\t\t\"@id\": \"https://www.example.org/legalPerson.json\",\n\t\t\"@type\": [\"VerifiableCredential\"],\n\t\t\"issuer\": \"http://gaiax.de\",\n\t\t\"issuanceDate\": \"2022-10-19T18:48:09Z\",\n\t\t\"credentialSubject\": {\n"%i
        csContext = "\t\t\t\"@context\": {\n\t\t\t\t\"dcat\": \"http://www.w3.org/ns/dcat#\",\n\t\t\t\t\"gax-core\": \"https://w3id.org/gaia-x/core#\",\n\t\t\t\t\"gax-trust-framework\": \"https://w3id.org/gaia-x/gax-trust-framework#\",\n\t\t\t\t\"xsd\": \"http://www.w3.org/2001/XMLSchema#\"\n\t\t\t},\n"
        attributes1 = "\t\t\t\"@id\": \"gax-trust-framework:Service%s\",\n\t\t\t\"@type\": \"gax-trust-framework:ServiceOffering\",\n\t\t\t\"gax-trust-framework:dataAccountExport\": {\n\t\t\t\t\"@type\": \"gax-trust-framework:DataAccountExport\",\n\t\t\t\t\"gax-trust-framework:accessType\": \"access type\",\n\t\t\t\t\"gax-trust-framework:formatType\": \"format type\",\n\t\t\t\t\"gax-trust-framework:requestType\": \"request type\"\n\t\t\t},\n" %i
        attributes2 = "\n\t\t\t\"gax-core:offeredBy\": {\n\t\t\t\t\"@id\": \"gax-trust-framework:Provider%s\"\n\t\t\t},\n\t\t\t\"gax-trust-framework:termsAndConditions\": {\n\t\t\t\t\"@type\": \"gax-trust-framework:TermsAndConditions\",\n\t\t\t\t\"gax-trust-framework:content\": {\n\t\t\t\t\t\"@type\": \"xsd:anyURI\",\n\t\t\t\t\t\"@value\": \"http://example.org/tac\"\n\t\t\t\t},\n\t\t\t\t\"gax-trust-framework:hash\": \"1234\"\n\t\t\t},\n"%i
        scalable = "\t\t\t\"dcat:keyword\": [\n"

        for j in range(1, attrNumber + 1):
            if j == 1:
                attributes2 = attributes2+scalable
            if j < attrNumber:
                attributes2 += "\t\t\t\t\"Keyword%s_%s\",\n" %(i,j)
            if j == attrNumber:
                attributes2 += "\t\t\t\t\"Keyword%s_%s\"\n\t\t\t],\n" % (i, j)

        ending = "\t\t\t\"gax-trust-framework:policy\": \"www.example.org/ServicePolicy\"\n\t\t}\n\t}\n}"

        completeSD = vp + csContext + attributes1 + attributes2 + ending

        text_file = open("service%s.jsonld"%i, "w")
        text_file.write(completeSD)
        text_file.close

def createLegalPersonSD(attrNumber, sdNumber):
    for i in range(1, sdNumber + 1):
        vp = "{\n\t\"@context\": [\"https://www.w3.org/2018/credentials/v1\"],\n\t\"@id\": \"http://example.edu/verifiablePresentation/self-description%s\",\n\t\"type\": [\"VerifiablePresentation\"],\n\t\"verifiableCredential\": {\n\t\t\"@context\": [\"https://www.w3.org/2018/credentials/v1\"],\n\t\t\"@id\": \"https://www.example.org/legalPerson.json\",\n\t\t\"@type\": [\"VerifiableCredential\"],\n\t\t\"issuer\": \"http://gaiax.de\",\n\t\t\"issuanceDate\": \"2022-10-19T18:48:09Z\",\n\t\t\"credentialSubject\": {\n" %i
        csContext = "\t\t\t\"@context\": {\n\t\t\t\t\"gax-trust-framework\": \"https://w3id.org/gaia-x/gax-trust-framework#\",\n\t\t\t\t\"xsd\": \"http://www.w3.org/2001/XMLSchema#\",\n\t\t\t\t\"vcard\": \"http://www.w3.org/2006/vcard/ns#\"\n\t\t\t},\n"
        attributes1 = "\t\t\t\"@id\": \"gax-trust-framework:Participant%s\",\n\t\t\t\"@type\": \"gax-trust-framework:LegalPerson\",\n\t\t\t\"gax-trust-framework:registrationNumber\": \"1234\",\n" % i
        attributes2 = "\t\t\t\"gax-trust-framework:legalAddress\": {	\n\t\t\t\t\"@type\": \"vcard:Address\",\n\t\t\t\t\"vcard:country-name\": \"Country\",\n\t\t\t\t\"vcard:locality\": \"Town Name\",\n\t\t\t\t\"vcard:postal-code\": \"1234\",\n\t\t\t\t\"vcard:street-address\": \"Street Name\"\n\t\t\t},\n\t\t\t\"gax-trust-framework:headquarterAddress\": {	\n\t\t\t\t\"@type\": \"vcard:Address\",\n\t\t\t\t\"vcard:country-name\": \"Country\",\n\t\t\t\t\"vcard:locality\": \"Town Name\",\n\t\t\t\t\"vcard:postal-code\": \"1234\",\n\t\t\t\t\"vcard:street-address\": \"Street Name\"\n\t\t\t},\n\t\t\t\"gax-gax-trust-framework:termsAndConditions\": {	\n\t\t\t\t\"@type\": \"gax-trust-framework:TermsAndConditions\",\n\t\t\t\t\"gax-trust-framework:content\": {\n\t\t\t\t\t\"@type\": \"xsd:anyURI\",\n\t\t\t\t\t\"@value\": \"http://example.org/tac\"\n\t\t\t\t },	\n\t\t\t\t\"gax-trust-framework:hash\": \"1234\"\n\t\t\t},\n"
        scalable = "\t\t\t\"gax-trust-framework:subOrganisation\": [\n"

        for j in range(1, attrNumber + 1):
            if j == 1:
                attributes2 = attributes2+scalable
            if j < attrNumber:
                attributes2 += "\t\t\t\t{\n\t\t\t\t\t\"@id\": \"http://example.org/Provider%s_%s\"\n\t\t\t\t},\n" %(i,j)
            if j == attrNumber:
                attributes2 += "\t\t\t\t{\n\t\t\t\t\t\"@id\": \"http://example.org/Provider%s_%s\"\n\t\t\t\t}\n\t\t\t],\n" % (i,  j)

        ending = "\t\t\t\"gax-trust-framework:legalName\": \"Provider Name\"\n\t\t}\n\t}\n}"

        completeSD = vp + csContext + attributes1 + attributes2 + ending

        text_file = open("legalPerson%s.jsonld" % i, "w")
        text_file.write(completeSD)
        text_file.close

if __name__ == '__main__':
    parser = argparse.ArgumentParser()
    parser.add_argument('-claimNr', help='Number of claims contained in the generated Self Description', type=int)
    parser.add_argument('-sdNr', help='Number of generated Self Description', type=int)
    parser.add_argument('-schema', help='Schema of generated Self Description (service or legalperson)')
    args = parser.parse_args()

    claimNumber = vars(args)["claimNr"]
    sdNumber = vars(args)["sdNr"]
    schema = vars(args)["schema"]

    if schema == "service":
        createServiceOfferingSD(claimNumber, sdNumber)
    elif schema == "legalperson":
        createLegalPersonSD(claimNumber, sdNumber)
    else:
        print("Error: Please enter valid schema.")