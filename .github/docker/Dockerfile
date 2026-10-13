FROM eclipse-temurin:21-jre

ARG SERVICE_NAME
ARG JAR_FILE=target/${SERVICE_NAME}-*.jar

WORKDIR /app
COPY ${JAR_FILE} app.jar

EXPOSE 8080
ENTRYPOINT ["java", "-jar", "app.jar"]
