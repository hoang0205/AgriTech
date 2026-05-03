FROM eclipse-temurin:21-jdk-jammy AS builder

WORKDIR /app

COPY gradlew .
COPY gradle gradle
COPY build.gradle.kts .
COPY settings.gradle.kts .

RUN chmod +x ./gradlew

RUN ./gradlew dependencies --no-daemon

COPY src src
RUN ./gradlew clean build -x test --no-daemon

FROM eclipse-temurin:21-jre-jammy

WORKDIR /app

COPY --from=builder /app/build/libs/*-SNAPSHOT.jar app.jar

EXPOSE 2507

# Khởi chạy ứng dụng
ENTRYPOINT ["java", "-jar", "app.jar"]