FROM node:24-alpine AS frontend
WORKDIR /frontend
COPY frontend/package.json frontend/package-lock.json ./
RUN npm ci
COPY frontend/ ./
RUN npm run build

FROM eclipse-temurin:21-jdk-alpine AS builder
WORKDIR /app

COPY backend/.mvn/ .mvn/
COPY backend/mvnw backend/pom.xml ./

COPY backend/src/ src/
RUN rm -rf src/main/resources/static && mkdir -p src/main/resources/static
COPY --from=frontend /frontend/dist/ src/main/resources/static/
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
