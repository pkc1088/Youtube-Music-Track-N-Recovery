# 1. Build Stage
FROM gradle:8.10.2-jdk17 AS builder
WORKDIR /app
COPY . .
RUN chmod +x ./gradlew
RUN ./gradlew build --no-daemon -x test

# 2. Run Stage
FROM eclipse-temurin:17-jdk-jammy
WORKDIR /app
COPY --from=builder /app/build/libs/app.jar ./app.jar

#ENV SPRING_PROFILES_ACTIVE=prod 필요 없을듯?
ENV TZ=Asia/Seoul
ENV HOSTNAME=0.0.0.0

#ENV PORT=8080
EXPOSE 8080

ENTRYPOINT ["java", "-jar", "app.jar"]
