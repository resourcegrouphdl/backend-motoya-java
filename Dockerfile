# Multi-stage build para optimizar el tamaño de la imagen

# Etapa 1: Build
FROM maven:3.9-eclipse-temurin-21-jammy AS build
WORKDIR /app

# Copiar archivos de Maven
COPY pom.xml .

# Descargar dependencias
RUN mvn dependency:go-offline -B

# Copiar el código fuente
COPY src src

# Compilar la aplicación
RUN mvn clean package -DskipTests -B


# Etapa 2: Runtime
FROM eclipse-temurin:21-jre-jammy
WORKDIR /app

# Crear usuario no-root (formato correcto para Debian/Ubuntu)
RUN addgroup --system spring \
    && adduser --system --ingroup spring spring

USER spring

# Copiar el JAR desde la etapa de build
COPY --from=build /app/target/*.jar app.jar

# Variables de entorno
ENV SPRING_PROFILES_ACTIVE=prod

# Cloud Run usa el puerto dinámico
ENV PORT=8080

EXPOSE 8080

# Configuración JVM optimizada
ENV JAVA_OPTS="-XX:+UseContainerSupport -XX:MaxRAMPercentage=75.0 -XX:+UseG1GC -XX:+UseStringDeduplication"

# Ejecutar aplicación
ENTRYPOINT ["sh", "-c", "java $JAVA_OPTS -jar app.jar"]