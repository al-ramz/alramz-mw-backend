FROM eclipse-temurin:21-jre

RUN useradd --system --uid 10001 app
WORKDIR /app
COPY target/risk-management-service-*.jar app.jar
EXPOSE 8080
USER 10001
ENTRYPOINT ["java", "-jar", "app.jar"]
