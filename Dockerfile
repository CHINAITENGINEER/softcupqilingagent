FROM maven:3.9.9-eclipse-temurin-21 AS build
WORKDIR /workspace
COPY pom.xml .
RUN mvn -B -DskipTests dependency:go-offline
COPY src ./src
RUN mvn -B -DskipTests package

FROM eclipse-temurin:21-jre
WORKDIR /app
RUN apt-get update \
    && apt-get install --yes --no-install-recommends curl \
    && rm -rf /var/lib/apt/lists/* \
    && addgroup --system safeops \
    && adduser --system --ingroup safeops safeops
COPY --from=build /workspace/target/qilingos-safeops-agent-*.jar app.jar
USER safeops
EXPOSE 8088
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
