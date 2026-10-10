FROM eclipse-temurin:25-jdk AS build
WORKDIR /workspace
COPY . .
RUN ./gradlew bootJar --no-daemon

FROM eclipse-temurin:25-jre
LABEL org.opencontainers.image.source=https://github.com/95twan/myroutine-v2
RUN apt-get update && apt-get install -y --no-install-recommends curl && rm -rf /var/lib/apt/lists/*
RUN useradd --system --uid 10001 app
WORKDIR /app
COPY --from=build /workspace/build/libs/myroutine-0.0.1-SNAPSHOT.jar app.jar
USER app
EXPOSE 8080 8081
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
