# Build stage - GraalVM native image
FROM ghcr.io/graalvm/native-image-community:21 AS build

# Install findutils (provides xargs, required by Gradle wrapper)
RUN microdnf install -y findutils && microdnf clean all

WORKDIR /app

# Copy gradle files first for better caching
COPY gradlew .
COPY gradle gradle
COPY build.gradle .
COPY settings.gradle .

# Make gradlew executable
RUN chmod +x gradlew

# Copy source code
COPY src src

# Build native executable
RUN ./gradlew build -Dquarkus.native.enabled=true -x test

# Runtime stage - minimal image
FROM quay.io/quarkus/quarkus-micro-image:2.0
WORKDIR /app

# Copy the native executable
COPY --from=build /app/build/*-runner /app/application

# Expose port
ENV PORT=8080
EXPOSE 8080

# Health check
HEALTHCHECK --interval=30s --timeout=3s --start-period=10s --retries=3 \
    CMD wget --no-verbose --tries=1 --spider http://localhost:8080/q/health || exit 1

# Run the native application
CMD ["./application", "-Dquarkus.http.host=0.0.0.0"]
