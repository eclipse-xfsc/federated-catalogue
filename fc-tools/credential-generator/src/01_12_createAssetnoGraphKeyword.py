import argparse

def createServiceOfferingCredential(attrNumber, assetNumber):
    for i in range(1, assetNumber + 1):
        vp = "{\n\t\"@context\": [\"https://www.w3.org/2018/credentials/v1\"],\n\t\"@id\": \"http://example.edu/verifiablePresentation/self-description%s\",\n\t\"type\": [\"VerifiablePresentation\"],\n\t\"verifiableCredential\": {\n\t\t\"@context\": [\"https://www.w3.org/2018/credentials/v1\"],\n\t\t\"@id\": \"https://www.example.org/legalPerson.json\",\n\t\t\"@type\": [\"VerifiableCredential\"],\n\t\t\"issuer\": \"http://gaiax.de\",\n\t\t\"issuanceDate\": \"2022-10-19T18:48:09Z\",\n\t\t\"credentialSubject\": {\n"%i
        csContext = "\t\t\t\"@context\": {\n\t\t\t\t\"dcat\": \"http://www.w3.org/ns/dcat#\",\n\t\t\t\t\"gx\": \"https://w3id.org/gaia-x/2511#\",\n\t\t\t\t\"xsd\": \"http://www.w3.org/2001/XMLSchema#\"\n\t\t\t},\n"
        attributes1 = "\t\t\t\"@id\": \"gx:Service%s\",\n\t\t\t\"@type\": \"gx:ServiceOffering\",\n\t\t\t\"gx:dataAccountExport\": {\n\t\t\t\t\"@type\": \"gx:DataAccountExport\",\n\t\t\t\t\"gx:accessType\": \"access type\",\n\t\t\t\t\"gx:formatType\": \"format type\",\n\t\t\t\t\"gx:requestType\": \"request type\"\n\t\t\t},\n" %i
        attributes2 = "\n\t\t\t\"gx:offeredBy\": {\n\t\t\t\t\"@id\": \"gx:Provider%s\"\n\t\t\t},\n\t\t\t\"gx:termsAndConditions\": {\n\t\t\t\t\"@type\": \"gx:TermsAndConditions\",\n\t\t\t\t\"gx:content\": {\n\t\t\t\t\t\"@type\": \"xsd:anyURI\",\n\t\t\t\t\t\"@value\": \"http://example.org/tac\"\n\t\t\t\t},\n\t\t\t\t\"gx:hash\": \"1234\"\n\t\t\t},\n"%i
        scalable = "\t\t\t\"dcat:keyword\": [\n"

        for j in range(1, attrNumber + 1):
            if j == 1:
                attributes2 = attributes2+scalable
            if j < attrNumber:
                attributes2 += "\t\t\t\t\"Keyword%s_%s\",\n" %(i,j)
            if j == attrNumber:
                attributes2 += "\t\t\t\t\"Keyword%s_%s\"\n\t\t\t],\n" % (i, j)

        ending = "\t\t\t\"gx:policy\": \"www.example.org/ServicePolicy\"\n\t\t}\n\t}\n}"

        completeCredential = vp + csContext + attributes1 + attributes2 + ending

        text_file = open("service%s.jsonld"%i, "w")
        text_file.write(completeCredential)
        text_file.close

def createLegalPersonCredential(attrNumber, assetNumber):
    for i in range(1, assetNumber + 1):
        vp = "{\n\t\"@context\": [\"https://www.w3.org/2018/credentials/v1\"],\n\t\"@id\": \"http://example.edu/verifiablePresentation/self-description%s\",\n\t\"type\": [\"VerifiablePresentation\"],\n\t\"verifiableCredential\": {\n\t\t\"@context\": [\"https://www.w3.org/2018/credentials/v1\"],\n\t\t\"@id\": \"https://www.example.org/legalPerson.json\",\n\t\t\"@type\": [\"VerifiableCredential\"],\n\t\t\"issuer\": \"http://gaiax.de\",\n\t\t\"issuanceDate\": \"2022-10-19T18:48:09Z\",\n\t\t\"credentialSubject\": {\n" %i
        csContext = "\t\t\t\"@context\": {\n\t\t\t\t\"gx\": \"https://w3id.org/gaia-x/2511#\",\n\t\t\t\t\"xsd\": \"http://www.w3.org/2001/XMLSchema#\",\n\t\t\t\t\"vcard\": \"http://www.w3.org/2006/vcard/ns#\"\n\t\t\t},\n"
        attributes1 = "\t\t\t\"@id\": \"gx:Participant%s\",\n\t\t\t\"@type\": \"gx:LegalPerson\",\n\t\t\t\"gx:registrationNumber\": \"1234\",\n" % i
        attributes2 = "\t\t\t\"gx:legalAddress\": {	\n\t\t\t\t\"@type\": \"vcard:Address\",\n\t\t\t\t\"vcard:country-name\": \"Country\",\n\t\t\t\t\"vcard:locality\": \"Town Name\",\n\t\t\t\t\"vcard:postal-code\": \"1234\",\n\t\t\t\t\"vcard:street-address\": \"Street Name\"\n\t\t\t},\n\t\t\t\"gx:headquarterAddress\": {	\n\t\t\t\t\"@type\": \"vcard:Address\",\n\t\t\t\t\"vcard:country-name\": \"Country\",\n\t\t\t\t\"vcard:locality\": \"Town Name\",\n\t\t\t\t\"vcard:postal-code\": \"1234\",\n\t\t\t\t\"vcard:street-address\": \"Street Name\"\n\t\t\t},\n\t\t\t\"gx:termsAndConditions\": {	\n\t\t\t\t\"@type\": \"gx:TermsAndConditions\",\n\t\t\t\t\"gx:content\": {\n\t\t\t\t\t\"@type\": \"xsd:anyURI\",\n\t\t\t\t\t\"@value\": \"http://example.org/tac\"\n\t\t\t\t },	\n\t\t\t\t\"gx:hash\": \"1234\"\n\t\t\t},\n"
        scalable = "\t\t\t\"gx:subOrganisation\": [\n"

        for j in range(1, attrNumber + 1):
            if j == 1:
                attributes2 = attributes2+scalable
            if j < attrNumber:
                attributes2 += "\t\t\t\t{\n\t\t\t\t\t\"@id\": \"http://example.org/Provider%s_%s\"\n\t\t\t\t},\n" %(i,j)
            if j == attrNumber:
                attributes2 += "\t\t\t\t{\n\t\t\t\t\t\"@id\": \"http://example.org/Provider%s_%s\"\n\t\t\t\t}\n\t\t\t],\n" % (i,  j)

        ending = "\t\t\t\"gx:legalName\": \"Provider Name\"\n\t\t}\n\t}\n}"

        completeCredential = vp + csContext + attributes1 + attributes2 + ending

        text_file = open("legalPerson%s.jsonld" % i, "w")
        text_file.write(completeCredential)
        text_file.close

if __name__ == '__main__':
    parser = argparse.ArgumentParser()
    parser.add_argument('-claimNr', help='Number of claims contained in the generated Credential', type=int)
    parser.add_argument('-assetNr', help='Number of generated Credentials', type=int)
    parser.add_argument('-schema', help='Schema of generated Credential (service or legalperson)')
    args = parser.parse_args()

    claimNumber = vars(args)["claimNr"]
    assetNumber = vars(args)["assetNr"]
    schema = vars(args)["schema"]

    if schema == "service":
        createServiceOfferingCredential(claimNumber, assetNumber)
    elif schema == "legalperson":
        createLegalPersonCredential(claimNumber, assetNumber)
    else:
        print("Error: Please enter valid schema.")