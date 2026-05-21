FROM eclipse-temurin:21-jre

WORKDIR /app
RUN mkdir -p /app/uploads
COPY target/contract-sys-backend-0.1.0-SNAPSHOT.jar /app/app.jar

EXPOSE 8080
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
