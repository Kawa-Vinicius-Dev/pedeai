FROM eclipse-temurin:21-jdk-alpine AS build
WORKDIR /workspace
COPY .mvn .mvn
COPY mvnw pom.xml ./
RUN chmod +x mvnw && ./mvnw -q -DskipTests dependency:go-offline
COPY src src
RUN ./mvnw -q -DskipTests package

FROM eclipse-temurin:21-jre-alpine
RUN addgroup -S pedeai && adduser -S pedeai -G pedeai
WORKDIR /app
COPY --from=build /workspace/target/pedeai-api-*.jar app.jar
USER pedeai
EXPOSE 8080
# Usa até 75% da memória do contêiner: em plano pequeno (512 MB a 1 GB) o padrão da JVM desperdiça ou estoura.
ENTRYPOINT ["java", "-XX:MaxRAMPercentage=75", "-XX:+ExitOnOutOfMemoryError", "-jar", "app.jar"]
