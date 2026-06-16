# Multi-stage build for Viglet Dumont ES.
# Ported from viglet/cloud docker/Dockerfile.dumont — the `viglet/jdk-build`
# base (eclipse-temurin:25-jdk + git) is inlined here so the build is
# self-contained and needs no externally published base image.
FROM eclipse-temurin:25-jdk AS build
RUN apt-get update && apt-get install -y git && rm -rf /var/lib/apt/lists/*
WORKDIR /src
ARG REPO_BRANCH=2026.3
ARG CACHE_BUST=none
# Reference CACHE_BUST in the RUN so a new SHA invalidates the git clone layer.
# `github_token` is a BuildKit secret. It's only present at build time via the
# runtime mount and never lands in any image layer. Optional — falls back to
# anonymous clone if the secret is empty.
RUN --mount=type=secret,id=github_token,required=false \
    echo "cache_bust=${CACHE_BUST}" && \
    if [ -s /run/secrets/github_token ]; then \
        URL="https://x-access-token:$(cat /run/secrets/github_token)@github.com/openviglet/dumont.git"; \
    else \
        URL="https://github.com/openviglet/dumont.git"; \
    fi && \
    git clone --depth 1 --branch ${REPO_BRANCH} "$URL" .
RUN chmod +x mvnw
RUN --mount=type=cache,target=/root/.m2 \
    --mount=type=cache,target=/src/dumont-react/node_modules \
    --mount=type=cache,target=/src/dumont-react/node \
    ./mvnw clean package -DskipTests \
    -pl '!aem/aem-plugin-sample,!assets/assets-plugin-sample,!db/db-plugin-sample,!web-crawler/wc-plugin-sample' 2>&1

FROM eclipse-temurin:25-jre
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
ENTRYPOINT ["sh", "-c", "exec java ${JAVA_OPTS} ${DEBUG_OPTS} -Djava.security.egd=file:/dev/./urandom -Dloader.path=${LOADER_PATH} -Dlogging.config=classpath:logback-spring-console.xml -jar /app/app.jar"]
