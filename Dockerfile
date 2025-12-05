# === Этап 1: Сборка приложения ===
FROM maven:3.9.6-eclipse-temurin-21 AS build
WORKDIR /app

# Копирование pom.xml для кэширования зависимостей
COPY pom.xml .
RUN mvn -B dependency:go-offline || true

# Копирование исходного кода
COPY src ./src

# Сборка проекта
RUN mvn -B clean package -DskipTests

# === Этап 2: Запуск приложения ===
FROM eclipse-temurin:21-jdk
WORKDIR /app

# Копирование JAR файла из этапа сборки
COPY --from=build /app/target/*.jar app.jar

# Порт для Gateway (8084 согласно application.properties)
EXPOSE 8084

# Переменная окружения для профиля
ENV SPRING_PROFILES_ACTIVE=docker

# Запуск приложения
ENTRYPOINT ["java", "-jar", "app.jar"]
