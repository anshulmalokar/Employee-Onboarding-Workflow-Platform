FROM maven:3.9.8-eclipse-temurin-17 AS builder
WORKDIR /app
COPY .. /app
RUN mvn -pl onboarding-service -am clean package -DskipTests

FROM eclipse-temurin:17-jre
WORKDIR /service
COPY --from=builder /app/onboarding-service/target/onboarding-service-1.0.0.jar app.jar
EXPOSE 8081
ENTRYPOINT ["java", "-jar", "app.jar"]
