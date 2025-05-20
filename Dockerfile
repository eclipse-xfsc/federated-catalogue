FROM --platform=$BUILDPLATFORM maven:3.9.6-eclipse-temurin-21 AS build

WORKDIR /app

COPY fc-service-api fc-service-api
COPY fc-service-client fc-service-client
COPY fc-test-support fc-test-support
COPY fc-service-core fc-service-core
COPY fc-service-server fc-service-server
COPY fc-demo-portal fc-demo-portal
COPY fc-tools fc-tools
COPY openapi openapi

COPY pom.xml pom.xml
COPY lombok.config lombok.config

RUN mvn clean install -DskipTests -Dcheckstyle.skip


FROM bellsoft/liberica-openjdk-alpine:21 as fc-service-server
COPY --from=build /app/fc-service-server/target/fc-service-server-*.jar fc-service-server.jar
ENTRYPOINT ["java", "-jar","/fc-service-server.jar"]

FROM bellsoft/liberica-openjdk-alpine:21 as fc-demo-portal
COPY --from=build /app/fc-demo-portal/target/fc-demo-portal-*.jar fc-demo-portal.jar
ENTRYPOINT ["java", "-jar","/fc-demo-portal.jar"]
