package eu.xfsc.fc.core.config;

import javax.xml.XMLConstants;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import javax.xml.validation.SchemaFactory;

import org.springframework.beans.factory.config.ConfigurableBeanFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Scope;
import org.xml.sax.SAXException;

/**
 * Shared secure XML parser factory configuration.
 *
 * <p>Both factories are scoped {@code prototype} because {@link DocumentBuilderFactory} and
 * {@link SchemaFactory} are documented as not thread-safe; consumers must inject via
 * {@link org.springframework.beans.factory.ObjectProvider} and call {@code getObject()}
 * for each parse to obtain a fresh instance.</p>
 */
@Configuration
public class XmlSecurityConfig {

  /**
   * Hardened {@link DocumentBuilderFactory} that resists XXE, billion-laughs and external
   * entity attacks. Mitigations follow the OWASP XML External Entity (XXE) Prevention
   * Cheat Sheet — see comments on each setting.
   */
  @Bean
  @Scope(ConfigurableBeanFactory.SCOPE_PROTOTYPE)
  public DocumentBuilderFactory secureDocumentBuilderFactory() throws ParserConfigurationException {
    DocumentBuilderFactory dbf = DocumentBuilderFactory.newInstance();
    dbf.setNamespaceAware(true);
    // Primary XXE mitigation: reject any input containing a DOCTYPE declaration.
    dbf.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
    // Defense-in-depth for parsers that ignore disallow-doctype-decl: block external entity loading.
    dbf.setFeature("http://xml.org/sax/features/external-general-entities", false);
    dbf.setFeature("http://xml.org/sax/features/external-parameter-entities", false);
    // Generic JAXP flag enabling secure-processing limits (e.g. caps entity expansion to mitigate
    // billion-laughs / quadratic-blowup denial of service).
    dbf.setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true);
    // Belt-and-braces: even if an entity slips through, do not expand it inline.
    dbf.setExpandEntityReferences(false);
    return dbf;
  }

  /**
   * Hardened {@link SchemaFactory} for W3C XML Schema 1.0. Blocks SSRF and external schema
   * inclusion attacks via {@code xs:include} / {@code xs:import}.
   */
  @Bean
  @Scope(ConfigurableBeanFactory.SCOPE_PROTOTYPE)
  public SchemaFactory secureXmlSchemaFactory() throws SAXException {
    SchemaFactory factory = SchemaFactory.newInstance(XMLConstants.W3C_XML_SCHEMA_NS_URI);
    // Enables JAXP secure-processing limits inside the schema parser itself.
    factory.setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true);
    // Block all URI-based DTD and schema loading — prevents SSRF via attacker-supplied schemas
    // that reference internal endpoints or fetch payloads from the network.
    factory.setProperty(XMLConstants.ACCESS_EXTERNAL_DTD, "");
    factory.setProperty(XMLConstants.ACCESS_EXTERNAL_SCHEMA, "");
    return factory;
  }
}
