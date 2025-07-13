# Stage 1: Build the application using a JDK image
FROM eclipse-temurin:17-jdk-jammy AS build

# Install Maven
RUN apt-get update && \
    apt-get install -y maven && \
    rm -rf /var/lib/apt/lists/*

# Set the working directory inside the container
WORKDIR /app

# Copy the Maven project files
COPY pom.xml .
COPY src ./src

# Build the application, skipping tests (tests are run in Jenkins pipeline)
RUN mvn clean package -DskipTests

# Stage 2: Create the final lightweight image with just JRE
FROM eclipse-temurin:17-jre-jammy

# Set the working directory
WORKDIR /app

# Copy the built JAR from the build stage
COPY --from=build /app/target/book-service-0.0.1-SNAPSHOT.jar app.jar

# Expose the port your Spring Boot application listens on
EXPOSE 8080

# Define the command to run your application
ENTRYPOINT ["java", "-jar", "app.jar"]