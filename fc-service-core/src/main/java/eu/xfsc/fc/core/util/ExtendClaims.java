package eu.xfsc.fc.core.util;

import org.apache.jena.rdf.model.*;
import org.apache.jena.rdf.model.impl.StatementImpl;
import org.apache.jena.vocabulary.RDF;

import java.io.ByteArrayOutputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Utility for extending RDF claims with credential subject annotations.
 * Adds a {@code claimsGraphUri} property linking claims to their source credential subject IRI.
 * For W3C credentials, this is the {@code credentialSubject.id}; for other RDF, it's the main subject IRI.
 * 
 * @see eu.xfsc.fc.core.pojo.CredentialClaim
 */
public class ExtendClaims {

    /**
     * Adds annotation property linking claims to their source credential subject IRI.
     * Uses a previously validated model containing claims.
     *
     * @param claims the RDF model containing claims to annotate
     * @param credentialSubject the credential's subject IRI (credentialSubject.id)
     * @return Triples as N-Triples string
     */
    public static String addPropertyGraphUri(Model claims, String credentialSubject) {
        Literal credentialSubjectLiteral = ResourceFactory.createStringLiteral(credentialSubject);
        Property claimsGraphUri = ResourceFactory.createProperty("https://w3id.org/gaia-x/2511#claimsGraphUri");

        List<Statement> additionalTriples = new ArrayList<>();

        StmtIterator triples = claims.listStatements();
        while (triples.hasNext()) {
            Statement triple = triples.next();
            Resource s = triple.getSubject();
            Property p = triple.getPredicate();
            RDFNode o = triple.getObject();
            additionalTriples.add(new StatementImpl(s, claimsGraphUri, credentialSubjectLiteral));

            if (o.isResource() && !p.equals(RDF.type)) {
                // URIs and blank nodes, but not literals
                additionalTriples.add(new StatementImpl(o.asResource(), claimsGraphUri, credentialSubjectLiteral));
            }
        }

        claims.add(additionalTriples);
        OutputStream outputstream = new ByteArrayOutputStream();
        claims.write(outputstream, "N-TRIPLES");
        return outputstream.toString();
    }

    public static Set<String> getMultivalProp(Model claims) {
        Set<String> multiprop = new HashSet<>();
        StmtIterator triples = claims.listStatements();
        while (triples.hasNext()) {
            Statement triple = triples.next();
            if (checkMultivalueProp(triple.getSubject(), triple.getPredicate()))
                multiprop.add(triple.getPredicate().toString());
        }
        return multiprop;
    }

    private static boolean checkMultivalueProp(Resource subject, Property predicate) {
        StmtIterator iter = subject.listProperties(predicate); 
        if (!iter.hasNext()) return false;
        iter.next();
        return iter.hasNext();
    }
}