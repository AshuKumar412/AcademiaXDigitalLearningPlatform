FROM eclipse-temurin:25-jdk AS build
WORKDIR /workspace/app
COPY mvnw .
COPY .mvn .mvn
COPY pom.xml .
COPY src src
RUN chmod +x ./mvnw && ./mvnw clean package -DskipTests

FROM eclipse-temurin:25-jre
WORKDIR /app
COPY --from=build /workspace/app/target/*.war app.war
EXPOSE 8082
ENTRYPOINT ["java", "-jar", "app.war"]
