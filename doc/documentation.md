# MARC Migrations Documentation

## Table of contents
<!-- TOC -->
* [MARC Migrations Documentation](#marc-migrations-documentation)
  * [Table of contents](#table-of-contents)
  * [Compiling](#compiling)
  * [Running it](#running-it)
  * [Docker](#docker)
    * [Build image](#build-image)
    * [Run container](#run-container)
    * [Docker compose](#docker-compose)
  * [Deploying the module](#deploying-the-module)
    * [Environment variables](#environment-variables)
  * [Integration](#integration)
    * [Folio modules communication](#folio-modules-communication)
  * [APIs](#apis)
    * [API marc-migrations](#api-marc-migrations)
<!-- TOC -->

## Compiling
```shell
mvn clean package
```

See that it says "BUILD SUCCESS" near the end.

## Running it
Run locally with proper environment variables set (see [Environment variables](#environment-variables) below)
on listening port 8081 (default listening port):

```shell
java -Dserver.port=8081 -jar target/mod-marc-migrations-*.jar
```

## Docker
### Build image
```shell
docker build -t dev.folio/mod-marc-migrations .
```

### Run container

```shell
docker run -t -i -p 8081:8081 dev.folio/mod-marc-migrations
```

### Docker compose
To run the module and all dependant infrastructure use `docker-compose`
```shell
cd docker
docker compose up --build --force-recreate --no-deps
```

## Deploying the module
### Environment variables
| Name                             | Default value          | Description                                                                                                                                                                                          |
|:---------------------------------|:-----------------------|:-----------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|
| ENV                              | folio                  | The logical name of the deployment, must be unique across all environments using the same shared Kafka/Elasticsearch clusters, `a-z (any case)`, `0-9`, `-`, `_` symbols only allowed                |
| DB_HOST                          | localhost              | Postgres hostname                                                                                                                                                                                    |
| DB_PORT                          | 5432                   | Postgres port                                                                                                                                                                                        |
| DB_USERNAME                      | folio_admin            | Postgres username                                                                                                                                                                                    |
| DB_PASSWORD                      | folio_admin            | Postgres username password                                                                                                                                                                           |
| DB_DATABASE                      | okapi_modules          | Postgres database name                                                                                                                                                                               |
| DB_MAXPOOLSIZE                   | 10                     | This property controls the maximum size that the pool is allowed to reach, including both idle and in-use connections                                                                                |
| DB_MINIMUM_IDLE                  | 10                     | This property controls the minimum number of idle connections that HikariCP tries to maintain in the pool                                                                                            |
| DB_CONNECTION_TIMEOUT            | 30000                  | This property controls the maximum number of milliseconds that a client will wait for a connection from the pool                                                                                     |
| DB_IDLE_TIMEOUT                  | 600000                 | This property controls the maximum amount of time that a connection is allowed to sit idle in the pool. This setting only applies when `DB_MINIMUM_IDLE` is defined to be less than `DB_MAXPOOLSIZE` |
| DB_KEEPALIVE_TIME                | 0                      | This property controls how frequently HikariCP will attempt to keep a connection alive, in order to prevent it from being timed out by the database or network infrastructure (0 - disabled)         |
| DB_MAX_LIFETIME                  | 1800000                | This property controls the maximum lifetime of a connection in the pool                                                                                                                              |
| DB_VALIDATION_TIMEOUT            | 5000                   | This property controls the maximum amount of time that a connection will be tested for aliveness. This value must be less than the `DB_CONNECTION_TIMEOUT`                                           |
| DB_INITIALIZATION_FAIL_TIMEOUT   | 30000                  | This property controls whether the pool will "fail fast" if the pool cannot be seeded with an initial connection successfully                                                                        |
| DB_LEAK_DETECTION_THRESHOLD      | 30000                  | This property controls the amount of time that a connection can be out of the pool before a message is logged indicating a possible connection leak (0 - disabled)                                   |
| OKAPI_URL                        | -                      | Okapi URL                                                                                                                                                                                            |
| SYSTEM_USER_USERNAME             | mod-marc-migrations    | Username for system user                                                                                                                                                                             |
| SYSTEM_USER_PASSWORD             | -                      | Password for system user                                                                                                                                                                             |
| RECORDS_CHUNK_SIZE               | 500                    | Number of records in one chunk for operation processing                                                                                                                                              |
| CHUNK_FETCH_IDS_COUNT            | 500                    | Number of record ids to fetch per query on chunks preparation phase. RECORDS_CHUNK_SIZE should be a divisor for this in order to maintain proper chunk size                                          |
| CHUNK_PERSIST_COUNT              | 1_000                  | Number of chunks to be constructed before persisting to db                                                                                                                                           |
| CHUNK_PROCESSING_MAX_PARALLELISM | 4                      | Max thread pool size for chunks processing                                                                                                                                                           |
| S3_URL                           | http://localhost:9000/ | S3 compatible service url                                                                                                                                                                            |
| S3_REGION                        | -                      | S3 compatible service region                                                                                                                                                                         |
| S3_BUCKET                        | marc-migrations        | S3 compatible service bucket                                                                                                                                                                         |
| S3_ACCESS_KEY_ID                 | -                      | S3 compatible service access key                                                                                                                                                                     |
| S3_SECRET_ACCESS_KEY             | -                      | S3 compatible service secret key                                                                                                                                                                     |
| S3_IS_AWS                        | false                  | Specify if AWS S3 is used as files storage                                                                                                                                                           |                     |                                                                                                                                                                                                      |

## Integration
### Folio modules communication
| Module name               | Interface                 | Notes                                                                      |
|---------------------------|---------------------------|----------------------------------------------------------------------------|
| mod-login                 | login                     | For system user creation and authentication                                |
| mod-permissions           | permissions               | For system user creation                                                   |
| mod-users                 | users                     | For system user creation                                                   |
| mod-source-record-manager | mapping-metadata-provider | For fetching MARC mapping metadata                                         |
| mod-source-record-storage | source-storage-records    | For having access to MARC records                                          |
| mod-entities-links        | authority-storage         | For having access to Authority records                                     |

## APIs
### API marc-migrations
The API provides management endpoint for MARC Migrations

| METHOD  | URL                              | Required permissions                   | DESCRIPTION                                                           |
|:--------|:---------------------------------|:---------------------------------------|:----------------------------------------------------------------------|
| POST    | `/marc-migrations`               | `marc-migrations.operations.item.post` | Create new MARC Migration operation                                   |
| GET     | `/marc-migrations/{operationId}` | `marc-migrations.operations.item.get`  | Get MARC Migration operation by ID                                    |
| PUT     | `/marc-migrations/{operationId}` | `marc-migrations.operations.item.put`  | Save Mapped Data for the newly created MARC Migration (with POST API) |
