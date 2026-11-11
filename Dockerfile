# syntax=docker/dockerfile:1
FROM maven:3.9.11-eclipse-temurin-21 AS build
WORKDIR /build
COPY pom.xml .
COPY src ./src
# Optional secrets support development environments with an HTTPS inspection proxy.
# A normal EC2 build needs neither secret. The temporary trust store is not retained.
RUN --mount=type=secret,id=maven_settings --mount=type=secret,id=proxy_ca \
    set -eu; \
    if [ -f /run/secrets/proxy_ca ]; then \
      cp "$JAVA_HOME/lib/security/cacerts" /tmp/build-cacerts; \
      keytool -importcert -noprompt -alias build-proxy -file /run/secrets/proxy_ca \
        -keystore /tmp/build-cacerts -storepass changeit; \
      export MAVEN_OPTS="-Djavax.net.ssl.trustStore=/tmp/build-cacerts -Djavax.net.ssl.trustStorePassword=changeit"; \
    fi; \
    if [ -f /run/secrets/maven_settings ]; then \
      mvn -B -ntp -s /run/secrets/maven_settings clean verify; \
    else \
      mvn -B -ntp clean verify; \
    fi; \
    rm -f /tmp/build-cacerts

FROM eclipse-temurin:21-jre-jammy
WORKDIR /app
COPY --from=build /build/target/fortune-mission.jar app.jar
USER 10001:10001
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "app.jar"]
