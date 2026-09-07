# Stage 1: Build
FROM eclipse-temurin:21-jdk-alpine AS builder
WORKDIR /app

COPY .mvn/ .mvn/
COPY mvnw pom.xml ./
RUN ./mvnw dependency:go-offline -B

COPY src/ src/
RUN ./mvnw package -DskipTests -B

# Stage 2: Runtime
FROM eclipse-temurin:21-jre-alpine
WORKDIR /app

RUN addgroup -S mrp && adduser -S mrp -G mrp
RUN mkdir -p /data/mrp/uploads && chown -R mrp:mrp /data/mrp

COPY --from=builder /app/target/*.jar app.jar

USER mrp
EXPOSE 8080

ENTRYPOINT ["java", "-jar", "app.jar"]
