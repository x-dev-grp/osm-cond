# syntax=docker/dockerfile:1.4

########## Build stage ##########
FROM maven:3.9.8-eclipse-temurin-21 AS build
WORKDIR /app

# Pull deps first for better caching
COPY pom.xml ./
RUN --mount=type=cache,target=/root/.m2 \
    --mount=type=secret,id=maven_settings,target=/root/.m2/settings.xml,required=false \
    mvn -B -U -DskipTests dependency:go-offline

# Compile/package
COPY src ./src
RUN --mount=type=cache,target=/root/.m2 \
    --mount=type=secret,id=maven_settings,target=/root/.m2/settings.xml,required=false \
    mvn -B -DskipTests package

########## Runtime stage ##########
FROM mcr.microsoft.com/openjdk/jdk:21-ubuntu
WORKDIR /app

# Let CI pass SERVICE_PORT; default 8089
ARG SERVICE_PORT=8089
ENV SERVER_PORT=${SERVICE_PORT}
ENV JAVA_TOOL_OPTIONS="-XX:+UseContainerSupport -XX:MaxRAMPercentage=75" \
    SPRING_PROFILES_ACTIVE=prod

COPY --from=build /app/target/*.jar /app/app.jar

EXPOSE ${SERVER_PORT}
ENTRYPOINT ["java","-jar","/app/app.jar"]
