FROM eclipse-temurin:17-jre
WORKDIR /service
COPY target/onboarding-orchestrator-1.0.0.jar app.jar
EXPOSE 8082
ENTRYPOINT ["java", "-jar", "app.jar"]
