FROM gradle:8.12-jdk21 AS build

WORKDIR /app

# Import extra CA certs if present (for corporate proxy environments)
COPY corporate-ca.pe[m] /tmp/
RUN if [ -f /tmp/corporate-ca.pem ]; then \
        csplit -f /tmp/cert- -b '%02d.pem' /tmp/corporate-ca.pem '/BEGIN CERTIFICATE/' '{*}' 2>/dev/null || true && \
        for cert in /tmp/cert-*.pem; do \
            if [ -s "$cert" ] && grep -q 'BEGIN CERTIFICATE' "$cert"; then \
                keytool -importcert -trustcacerts -noprompt \
                    -alias "corporate-ca-$(basename $cert)" \
                    -file "$cert" \
                    -keystore "$JAVA_HOME/lib/security/cacerts" \
                    -storepass changeit 2>/dev/null || true; \
            fi; \
        done; \
    fi

COPY build.gradle.kts settings.gradle.kts ./
COPY --from=grunnmur . ./grunnmur/
RUN gradle dependencies --no-daemon

COPY src ./src
RUN gradle build -x test --no-daemon

FROM eclipse-temurin:25-jre-alpine

WORKDIR /app
COPY --from=build /app/build/libs/*-all.jar app.jar

EXPOSE 8083

CMD ["java", "-jar", "app.jar"]
