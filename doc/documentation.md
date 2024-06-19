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
    * [Registering MARC Migration operation](#registering-marc-migration-operation)
        * [Request body](#request-body)
        * [Response](#response)
    * [Tracking the state of MARC Migration operation](#tracking-the-state-of-marc-migration-operation)
        * [Response](#response-1)
    * [Initiating the Data Saving phase for the MARC Migration operation](#initiating-the-data-saving-phase-for-the-marc-migration-operation)
        * [Request body](#request-body-1)
    * [Full scenario for Authority records migration](#full-scenario-for-authority-records-migration)
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

| METHOD  | URL                              | Required permissions                   | DESCRIPTION                                                       |
|:--------|:---------------------------------|:---------------------------------------|:------------------------------------------------------------------|
| POST    | `/marc-migrations`               | `marc-migrations.operations.item.post` | Register new MARC Migration operation                             |
| GET     | `/marc-migrations/{operationId}` | `marc-migrations.operations.item.get`  | Track the state of MARC Migration operation by operation ID       |
| PUT     | `/marc-migrations/{operationId}` | `marc-migrations.operations.item.put`  | Initiating the Data Saving phase for the MARC Migration operation |


### Registering MARC Migration operation

Registering a migration operation consist of creating the new migration operation record and initiating async Data Mapping Job

Send a POST request to create Marc Migration operation
`POST /marc-migrations`
##### Request body
```json
{
  "entityType": "authority",
  "operationType": "remapping"
}
```

Operation type specifies the supported migration scenario for the specified entity type. Provided example request body can be used to register a migration operation for updating the existing authority records when mapping rules are changed

##### Response
```json
{
    "id": "99fa24e1-d0de-4f20-a122-5ecfe9dec539",
    "userId": "acf0af69-a463-458e-adc6-392045739ba0",
    "entityType": "authority",
    "operationType": "remapping",
    "status": "new",
    "totalNumOfRecords": 1141,
    "mappedNumOfRecords": 0,
    "savedNumOfRecords": 0
}
```
In a response there should be ```status: new``` indicating this is a newly initiated migration operation, ```totalNumOfRecords``` - number of records considered for migration and ```id``` - UUID of the operation

Calling `POST /marc-migrations`, in addition to creating the new operation, also launches the async Job for Data (records/entities) Mapping using mapping rules and this Job's status can be tracked using the ```GET /marc-migrations/{operationId}```

### Tracking the state of MARC Migration operation

Send a GET request specifying the operation id as request param to track the state of migration operation:
```GET /marc-migrations/{operationId}```
##### Response
```json
{
    "id": "99fa24e1-d0de-4f20-a122-5ecfe9dec539",
    "userId": "acf0af69-a463-458e-adc6-392045739ba0",
    "entityType": "authority",
    "operationType": "remapping",
    "status": "data_mapping",
    "totalNumOfRecords": 1141,
    "mappedNumOfRecords": 850,
    "savedNumOfRecords": 0
}
```
When some async job is running for migration operation then calling the above GET endpoint will report the status of the job. Currently, two types of async jobs are run for a migration. They are either for Data Mapping or Data Saving phases of the operation.
Response will have the following values for ```"status"``` depending on whether async job is in process, finished with success or finished with failure:

* ```"data_mapping"``` or ```"data_saving"``` - job is in process for data mapping or data saving
* ```"data_mapping_completed"``` or ```"data_saving_sompleted"``` - job is finished with success for data mapping or data saving
* ```"data_mapping_failed"``` or ```"data_saving_failed"``` - job is finished with failure for data mapping or data saving

Also, when the job is finished successfully fields ```"mappedNumOfRecords"``` and ```"savedNumOfRecords"``` should have the values equal to the one from ```"totalNumOfRecords"```

### Initiating the Data Saving phase for the MARC Migration operation

Send a PUT request to start the Data Saving phase of migration operation.
```PUT /marc-migrations/{operationId}```

##### Request body
```json
{
  "status": "data_saving"
}
```

This runs async job for saving the records which were previously mapped when the migration was registered and async job for that mapping is completed with success. Initiating Data Saving for the operation with failed Data Mapping phase is not possible and respective error will be received if PUT endpoint is called in this case.

After calling the above PUT endpoint, the state of the migration and the status of async job for Data Saving can be tracked with ```GET /marc-migrations/{operationId}``` endpoint

### Full scenario for Authority records migration

1) Register MARC Migration operation by sending ```POST /marc-migrations```

   If registration is successful you will receive status code 201 and in response among other fields there will be ID and ```"status": "new"``` of new operation.
2) Using the new operation ID call ```GET /marc-migrations/{operationId}``` to track the state and status of Data Mapping phase of the migration

   When Data Mapping phase is completed with success there should be ```"status": "data_mapping_completed"``` and same values for the fields ```"mappedNumOfRecords"``` and ```"totalNumOfRecords"``` in response or ```"status": "data_mapping_failed"``` in case the mapping is finished with some failure.

3) After having all the authority records being mapped successfully the Data Saving phase for the migration can be started with ```PUT /marc-migrations/{operationId}```

4) In same way as in step 2, track the state and status of Data Saving phase of the migration with ```GET /marc-migrations/{operationId}```

   This time, for the successful Data Saving there should be ```"status": "data_saving_completed"``` and same values for the fields ```"savedNumOfRecords"``` and ```"totalNumOfRecords"``` in response or ```"status": "data_saving_failed"``` in case the saving is finished with some failure.
