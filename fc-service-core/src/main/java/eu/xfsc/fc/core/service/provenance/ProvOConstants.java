package eu.xfsc.fc.core.service.provenance;

import lombok.AccessLevel;
import lombok.NoArgsConstructor;

@NoArgsConstructor(access = AccessLevel.PRIVATE)
final class ProvOConstants {

  static final String NAMESPACE = "http://www.w3.org/ns/prov#";
  static final String RDF_NAMESPACE = "http://www.w3.org/1999/02/22-rdf-syntax-ns#";
  static final String DCS_NAMESPACE = "https://w3id.org/facis/dcs/1#";
  static final String XSD_DATETIME = "http://www.w3.org/2001/XMLSchema#dateTime";

  static final String RDF_TYPE = RDF_NAMESPACE + "type";
  static final String DCS_ACTION = DCS_NAMESPACE + "action";
}
