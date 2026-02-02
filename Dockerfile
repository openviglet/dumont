FROM eclipse-temurin:21-jre-alpine

LABEL maintainer="Viglet <support@viglet.com>"
LABEL description="Viglet Dumont Enterprise Search Connector"
LABEL version="2026.1.4"

# Create app directory
WORKDIR /app

# Add non-root user for security
RUN addgroup -S dumont && adduser -S dumont -G dumont

# Copy the application JAR
COPY connector/connector-app/target/dumont-connector-*.jar /app/dumont.jar

# Change ownership to non-root user
RUN chown -R dumont:dumont /app

# Switch to non-root user
USER dumont

# Expose application port
EXPOSE 8080

# Health check
HEALTHCHECK --interval=30s --timeout=3s --start-period=60s --retries=3 \
  CMD wget --quiet --tries=1 --spider http://localhost:8080/actuator/health || exit 1

# JVM options for containerized environment
ENV JAVA_OPTS="-XX:+UseContainerSupport -XX:MaxRAMPercentage=75.0 -XX:InitialRAMPercentage=50.0"

# Run the application
ENTRYPOINT ["sh", "-c", "java $JAVA_OPTS -jar /app/dumont.jar"]
