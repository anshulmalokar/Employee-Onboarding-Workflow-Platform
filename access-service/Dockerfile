FROM maven:3.9.8-eclipse-temurin-17 AS builder
WORKDIR /app
COPY .. /app
RUN mvn -pl access-service -am clean package -DskipTests

FROM eclipse-temurin:17-jre
WORKDIR /service
COPY --from=builder /app/access-service/target/access-service-1.0.0.jar app.jar
EXPOSE 8085
ENTRYPOINT ["java", "-jar", "app.jar"]
