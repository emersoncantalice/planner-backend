FROM maven:3.9.11-eclipse-temurin-21 AS build
WORKDIR /app
COPY pom.xml .
COPY .mvn .mvn
COPY mvnw mvnw
COPY mvnw.cmd mvnw.cmd
COPY src src
RUN mvn -q -DskipTests package

FROM eclipse-temurin:21-jre
WORKDIR /app
COPY --from=build /app/target/planner-backend-0.0.1-SNAPSHOT.jar app.jar
RUN mkdir -p /app/data
ENV PLANNER_DATA_DIR=/app/data
VOLUME ["/app/data"]
EXPOSE 8080
ENTRYPOINT ["java","-jar","/app/app.jar"]
