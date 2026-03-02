FROM maven:3.9.8-eclipse-temurin-17 AS builder
WORKDIR /app
COPY .. /app
RUN mvn -pl device-service -am clean package -DskipTests

FROM eclipse-temurin:17-jre
WORKDIR /service
COPY --from=builder /app/device-service/target/device-service-1.0.0.jar app.jar
EXPOSE 8084
ENTRYPOINT ["java", "-jar", "app.jar"]
