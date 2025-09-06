# Project Overview

This is a Spring Boot microservice that acts as a composite service for a product. It aggregates data from three other microservices: `product-service`, `recommendation-service`, and `review-service`. It provides a unified API for clients to interact with product data.

The project uses the following technologies:

*   **Java 21**
*   **Spring Boot 3.5.4**
*   **Spring Cloud 2025.0.0**
*   **Spring WebFlux** for reactive programming
*   **Spring Cloud Stream** with **RabbitMQ** and **Kafka** for messaging
*   **Spring Cloud Netflix Eureka** for service discovery
*   **Maven** for dependency management
*   **Docker** for containerization

## Architecture

The `product-composite-service` exposes a REST API for managing products. When a client sends a request to the composite service, it communicates with the other microservices to fulfill the request.

*   **GET /product/{productId}:** Retrieves the product, recommendations, and reviews for the given product ID and aggregates them into a single response.
*   **POST /product:** Creates a new product, and sends events to the `product-service`, `recommendation-service`, and `review-service` to create the corresponding resources.
*   **DELETE /product/{productId}:** Deletes a product and sends events to the other services to delete the associated resources.

# Building and Running

## Prerequisites

*   Java 21
*   Maven
*   Docker

## Building

To build the project, run the following command:

```bash
./mvnw clean package
```

## Running

To run the project, you can use the following command:

```bash
java -jar target/composite-0.0.1-SNAPSHOT.jar
```

The service will be available at `http://localhost:7000`.

## Running with Docker

To run the project with Docker, first build the Docker image:

```bash
docker build -t product-composite-service .
```

Then, run the Docker container:

```bash
docker run -p 7000:8080 product-composite-service
```

# Development Conventions

*   The project follows the standard Spring Boot project structure.
*   The code is written in a reactive style using Project Reactor.
*   The service communicates with other services asynchronously using messaging (RabbitMQ/Kafka) for create and delete operations, and synchronously using HTTP for get operations.
*   The project uses Eureka for service discovery.
*   The project uses `springdoc-openapi` to generate OpenAPI documentation. The documentation is available at `http://localhost:7000/openapi/swagger-ui.html`.
