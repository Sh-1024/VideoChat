FROM eclipse-temurin:25 AS builder
WORKDIR /app

COPY .mvn .mvn
COPY mvnw pom.xml ./

RUN chmod +x mvnw

RUN ./mvnw dependency:go-offline

COPY src ./src

RUN ./mvnw clean package -DskipTests

FROM eclipse-temurin:25
WORKDIR /app

RUN groupadd -r videochat && useradd -r -g videochat videochat

COPY --from=builder /app/target/*.jar app.jar

RUN chown videochat:videochat app.jar
USER videochat

EXPOSE 8080
ENTRYPOINT ["java", "-jar", "app.jar"]