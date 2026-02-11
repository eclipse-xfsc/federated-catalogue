FROM --platform=$BUILDPLATFORM maven:3.9.6-eclipse-temurin-21 AS build

WORKDIR /app

COPY fc-service-api fc-service-api
COPY fc-service-client fc-service-client
COPY fc-test-support fc-test-support
COPY fc-service-core fc-service-core
COPY fc-service-server fc-service-server
COPY fc-demo-portal fc-demo-portal
COPY fc-graphdb-neo4j fc-graphdb-neo4j
COPY fc-graphdb-fuseki fc-graphdb-fuseki
COPY fc-tools fc-tools
COPY openapi openapi

COPY pom.xml pom.xml
COPY lombok.config lombok.config

RUN mvn clean install -DskipTests -Dcheckstyle.skip


FROM bellsoft/liberica-openjdk-alpine:21 AS fc-service-server
# Import Zscaler certificate into runtime Java trust store
COPY docker/certs/ZscalerRootCertificate.crt /tmp/ZscalerRootCertificate.crt
RUN keytool -import -trustcacerts -keystore $JAVA_HOME/lib/security/cacerts \
    -storepass changeit -noprompt -alias zscaler-root \
    -file /tmp/ZscalerRootCertificate.crt || true
COPY --from=build /app/fc-service-server/target/fc-service-server-*.jar fc-service-server.jar
ENTRYPOINT ["java", "-jar","/fc-service-server.jar"]

FROM bellsoft/liberica-openjdk-alpine:21 AS fc-demo-portal
# Import Zscaler certificate into runtime Java trust store
COPY docker/certs/ZscalerRootCertificate.crt /tmp/ZscalerRootCertificate.crt
RUN keytool -import -trustcacerts -keystore $JAVA_HOME/lib/security/cacerts \
    -storepass changeit -noprompt -alias zscaler-root \
    -file /tmp/ZscalerRootCertificate.crt || true
COPY --from=build /app/fc-demo-portal/target/fc-demo-portal-*.jar fc-demo-portal.jar
ENTRYPOINT ["java", "-jar","/fc-demo-portal.jar"]

FROM neo4j:5.18.0 AS neo4j-with-zscaler
# Import Zscaler certificate into system trust store for plugin downloads
COPY docker/certs/ZscalerRootCertificate.crt /tmp/ZscalerRootCertificate.crt
# Neo4j uses Java, so import into Java keystore
RUN keytool -import -trustcacerts -keystore /opt/java/openjdk/lib/security/cacerts \
    -storepass changeit -noprompt -alias zscaler-root \
    -file /tmp/ZscalerRootCertificate.crt || true
# Also add to system CA certificates for curl/wget
RUN cp /tmp/ZscalerRootCertificate.crt /usr/local/share/ca-certificates/ZscalerRootCertificate.crt && \
    update-ca-certificates
