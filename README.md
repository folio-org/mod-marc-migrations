# mod-marc-migrations

Copyright (C) 2023 The Open Library Foundation

This software is distributed under the terms of the Apache License,
Version 2.0. See the file "[LICENSE](LICENSE)" for more information.

## Introduction
Module designed for high-performance migration of MARC records.

## Running the Module

### Using Docker Compose (Recommended)

The recommended way to run the module locally is using Docker Compose. This provides a complete development environment with all dependencies.

```shell
# Build the module JAR
mvn clean package -DskipTests

# Start all services (infrastructure + module)
docker compose -f docker/app-docker-compose.yml up -d

# View logs
docker compose -f docker/app-docker-compose.yml logs -f mod-marc-migrations
```

For detailed Docker Compose documentation, see [docker/README.md](docker/README.md).

### Local Development with IntelliJ IDEA

For local development, you can run the application directly from IntelliJ IDEA with the `dev` profile. Spring Boot will automatically start the required infrastructure services (PostgreSQL, Kafka, MinIO) using Docker Compose.

1. Open the project in IntelliJ IDEA
2. Run the main application class with the `dev` profile active
3. Spring Boot will automatically start infrastructure containers from `docker/infra-docker-compose.yml`

### Manually Running the Module

Run the module locally on the default listening port (8081) with infrastructure services:

```shell
# Start infrastructure services
docker compose -f docker/infra-docker-compose.yml up -d

# Run the module
mvn spring-boot:run
```

## Docker

### Building the Docker Image

Build the docker container with:

```shell
mvn clean package -DskipTests
docker build -t mod-marc-migrations .
```

### Running with Docker Compose

The project includes a comprehensive Docker Compose setup with two main configurations:

1. **Infrastructure only** (`infra-docker-compose.yml`) - PostgreSQL, Kafka, MinIO, and supporting services
2. **Full stack** (`app-docker-compose.yml`) - Infrastructure + mod-marc-migrations module with scalable instances

```shell
# Run infrastructure only (for local development)
docker compose -f docker/infra-docker-compose.yml up -d

# Run full stack with module instances
docker compose -f docker/app-docker-compose.yml up -d

# Scale module instances
docker compose -f docker/app-docker-compose.yml up -d --scale mod-marc-migrations=3
```

See [docker/README.md](docker/README.md) for detailed documentation on:
- Configuration options via `.env` file
- Multiple development workflows
- Service descriptions and ports
- Troubleshooting guide
- Best practices

## Additional Information
### Issue tracker
See project [MODMARCMIG](https://issues.folio.org/browse/MODMARCMIG) at the [FOLIO issue tracker](https://dev.folio.org/guidelines/issue-tracker/).

### API Documentation
This module's [API documentation](https://dev.folio.org/reference/api/#mod-marc-migrations).

### Module Documentation
This module's [Documentation](doc/documentation.md).

### Code analysis
[SonarQube analysis](https://sonarcloud.io/dashboard?id=org.folio%3Amod-marc-migrations).

### Download and configuration
The built artifacts for this module are available.
See [configuration](https://dev.folio.org/download/artifacts) for repository access,
and the [Docker image](https://hub.docker.com/r/folioorg/mod-marc-migrations/)
