# =============================================================================
# Internal Wallet Service - Dockerfile
# Multi-stage build: build with Maven, run with slim JRE image
# =============================================================================

# ---- Stage 1: Build ----
FROM maven:3.9-eclipse-temurin-21 AS builder

WORKDIR /app

# Copy pom.xml first and download dependencies separately.
# This layer is cached and only re-run when pom.xml changes,
# making subsequent builds much faster.
COPY pom.xml .
RUN mvn dependency:go-offline -q

# Copy source and build the fat JAR
COPY src ./src
RUN mvn package -DskipTests -q

# ---- Stage 2: Runtime ----
# eclipse-temurin:21-jre-alpine gives us a minimal JRE (~180 MB)
# compared to the full JDK (~600 MB).
FROM eclipse-temurin:21-jre-alpine

# Install curl for Docker health checks
RUN apk add --no-cache curl

WORKDIR /app

# Copy only the fat JAR from the build stage
COPY --from=builder /app/target/*.jar app.jar

EXPOSE 8080

# Use exec form to ensure the JVM receives SIGTERM for graceful shutdown
ENTRYPOINT ["java", "-jar", "app.jar"]
