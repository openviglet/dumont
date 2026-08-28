# Multi-stage build for Viglet Dumont ES.
# Builds straight from THIS repository's working tree (the Docker build context).
# The Dockerfile lives in the repo, so there is nothing to clone — the source is
# already here. See .dockerignore for what is kept out of the context (target/,
# node_modules, ...); .git is kept so the git-commit-id plugin can stamp the
# build version.
#
# Java 21 (NOT 25): embedded Artemis 2.43.0 (via Netty) crashes on Java 25 with
# "Cannot clean arbitrary ByteBuffer instances" (CleanerJava25) while loading its
# NIO journal, which takes down the broker and every JMS listener.
FROM eclipse-temurin:21-jdk AS build
WORKDIR /src
ARG CACHE_BUST=none
COPY . .
RUN chmod +x mvnw
# Build the connector + all connector plugins (samples excluded). The frontend
# (dumont-react) is built by the connector-app module via the frontend Maven
# plugin. Caches speed up rebuilds; nothing is fetched from the project's own
# Git remote.
RUN --mount=type=cache,target=/root/.m2 \
    --mount=type=cache,target=/src/dumont-react/node_modules \
    --mount=type=cache,target=/src/dumont-react/node \
    ./mvnw clean package -DskipTests \
    -pl '!aem/aem-plugin-sample,!assets/assets-plugin-sample,!db/db-plugin-sample,!web-crawler/wc-plugin-sample' 2>&1

FROM eclipse-temurin:21-jre
ARG CACHE_BUST=none
LABEL org.opencontainers.image.revision=$CACHE_BUST
RUN groupadd -r java && useradd -r -g java -u 1001 java
WORKDIR /app
COPY --from=build /src/connector/connector-app/target/dumont-connector.jar app.jar
# Bundle every connector plugin (AEM, EDS, DB, Web Crawler, Assets) so a single
# image supports all connector types at once. They are added to the application
# classpath at startup via Spring Boot's PropertiesLauncher `loader.path`, which
# lets each plugin's Spring components be discovered and several plugins coexist.
COPY --from=build /src/aem/aem-plugin/target/aem-plugin.jar /app/libs/aem-plugin.jar
COPY --from=build /src/eds/eds-plugin/target/eds-plugin.jar /app/libs/eds-plugin.jar
COPY --from=build /src/db/db-plugin/target/db-plugin.jar /app/libs/db-plugin.jar
COPY --from=build /src/web-crawler/wc-plugin/target/web-crawler-plugin.jar /app/libs/web-crawler-plugin.jar
COPY --from=build /src/assets/assets-plugin/target/assets-plugin.jar /app/libs/assets-plugin.jar
RUN mkdir -p /app/store && chown -R java:java /app
USER java
VOLUME ["/app/store"]
EXPOSE 30130
ENV JAVA_OPTS="-Xmx512m -Xms512m"
ENV DEBUG_OPTS=""
# LOADER_PATH points PropertiesLauncher at the bundled connector plugins.
ENV LOADER_PATH="/app/libs"
# No -Dlogging.config here (D86). It used to pin the console-only profile, which switches the
# indexing log off — and a -D system property outranks the mounted dumont-connector.properties,
# so the log could not be enabled in a container at all. Every profile now writes to the console,
# so stdout for the orchestrator costs nothing; dumont.logging.engine picks the profile (D85).
ENTRYPOINT ["sh", "-c", "exec java ${JAVA_OPTS} ${DEBUG_OPTS} -Djava.security.egd=file:/dev/./urandom -Dloader.path=${LOADER_PATH} -jar /app/app.jar"]
