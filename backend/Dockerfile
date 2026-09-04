FROM docker.1ms.run/library/maven:3.9.9-eclipse-temurin-21 AS build
WORKDIR /workspace

COPY settings.xml /root/.m2/settings.xml
COPY pom.xml .
RUN mvn -B -ntp dependency:go-offline

COPY src ./src
RUN mvn -B -ntp -DskipTests package

FROM docker.1ms.run/library/eclipse-temurin:21-jre-alpine
WORKDIR /app

RUN addgroup -S agentflow && adduser -S agentflow -G agentflow
COPY --from=build /workspace/target/*.jar /app/agentflow-backend.jar

USER agentflow
EXPOSE 8084
ENTRYPOINT ["java", "-jar", "/app/agentflow-backend.jar"]
