# syntax=docker/dockerfile:1

FROM eclipse-temurin:21-jdk-jammy AS build

WORKDIR /workspace

COPY gradle gradle
COPY gradlew build.gradle settings.gradle ./
RUN --mount=type=cache,target=/root/.gradle \
    ./gradlew --no-daemon dependencies

COPY src src
RUN --mount=type=cache,target=/root/.gradle \
    ./gradlew --no-daemon bootJar -x test && \
    java -Djarmode=tools -jar build/libs/*.jar extract --layers --destination extracted

FROM eclipse-temurin:21-jre-jammy AS runtime

RUN groupadd --system spring && \
    useradd --system --gid spring --home-dir /application spring

WORKDIR /application

COPY --from=build --chown=spring:spring /workspace/extracted/dependencies/ ./
COPY --from=build --chown=spring:spring /workspace/extracted/spring-boot-loader/ ./
COPY --from=build --chown=spring:spring /workspace/extracted/snapshot-dependencies/ ./
COPY --from=build --chown=spring:spring /workspace/extracted/application/ ./

USER spring
EXPOSE 8080

ENTRYPOINT ["java", "-jar", "application.jar"]
