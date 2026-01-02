## v2.0.5 2026-01-02
### Bugfixes
* Upgrade di-processing-core to v4.4.8 to fix deserialization errors ([MODMARCMIG-93](https://folio-org.atlassian.net/browse/MODMARCMIG-93))

---

## v2.0.4 2025-12-09
### Features
* Add default empty values for S3_SECRET_ACCESS_KEY and S3_ACCESS_KEY_ID ([MODMARCMIG-87](https://folio-org.atlassian.net/browse/MODMARCMIG-87))

---

## v2.0.3 2025-09-19
### Features
* Create endpoints that will provide an error report ([MODMARCMIG-52](https://folio-org.atlassian.net/browse/MODMARCMIG-52))
* Implement endpoint to execute only selected mapping chunks ([MODMARCMIG-47](https://folio-org.atlassian.net/browse/MODMARCMIG-47))
* Implement endpoint to execute only selected saving chunks ([MODMARCMIG-48](https://folio-org.atlassian.net/browse/MODMARCMIG-48))
* Skip SRS MARC-AUTHORITY records if linked entities do not exist ([MODMARCMIG-76](https://folio-org.atlassian.net/browse/MODMARCMIG-76))

---

## v2.0.2 2025-08-19

### Features
* Skip SRS MARC-BIB records if linked entities do not exist ([MODMARCMIG-68](https://folio-org.atlassian.net/browse/MODMARCMIG-68))

---

## v2.0.1 2025-04-18

### Bugfixes
* Upgrade di-processing-core to v4.4.1 to fix instance Date type field mapping ([MODMARCMIG-55](https://folio-org.atlassian.net/browse/MODMARCMIG-55))

### Dependencies
* Bump `data-import-processing-core` from `4.4.0` to `4.4.1`

---

## v2.0.0 2025-03-12
### Breaking changes
* Upgrade to Java 21 ([MODMARCMIG-42](https://folio-org.atlassian.net/browse/MODMARCMIG-42))

### Features
* Add optional API parameter to control Kafka entity change events for remapping operation saving step ([MODMARCMIG-37](https://folio-org.atlassian.net/browse/MODMARCMIG-37))

### Tech Dept
* Add users.item.put to tenant endpoint module permissions ([MODMARCMIG-40](https://folio-org.atlassian.net/browse/MODMARCMIG-40))

### Dependencies
* Bump `spring-boot` from `3.3.5` to `3.4.3`
* Bump `folio-spring-support` from `8.2.0` to `9.0.0`
* Bump `folio-s3-client` from `2.2.0` to `2.3.0`
* Bump `data-import-processing-core` from `4.3.0` to `4.4.0`
* Bump `aws-sdk-java` from `2.29.4` to `2.30.38`
* Bump `mapstruct` from `1.6.2` to `1.6.3`

---

## v1.0.0 2024-10-31
### New APIs versions
* Provides `marc-migrations v1.0`
* Requires `login v7.3`
* Requires `permissions v5.6`
* Requires `users v16.0`
* Requires `authority-storage v2.0`
* Requires `instance-storage v11.0`
* Requires `instance-storage-bulk v1.0`
* Requires `source-storage-records v3.3`
* Requires `mapping-metadata-provider v1.1`

### Features
* Implement endpoint to create new migration operation ([MODMARCMIG-5](https://issues.folio.org/browse/MODMARCMIG-5))
* Implement endpoint to get migration operation by ID ([MODMARCMIG-8](https://issues.folio.org/browse/MODMARCMIG-8))
* Implement async chunks preparation on migration operation creation ([MODMARCMIG-6](https://issues.folio.org/browse/MODMARCMIG-6))
* Implement async records mapping mechanism for MARC migration (Process Chunks) ([MODMARCMIG-7](https://issues.folio.org/browse/MODMARCMIG-7))
* Implement async mapped records saving mechanism for MARC migration (Process Chunks) ([MODMARCMIG-9](https://issues.folio.org/browse/MODMARCMIG-9))
* Prepare chunks of MARC Bibs for mapping ([MODMARCMIG-17](https://folio-org.atlassian.net/browse/MODMARCMIG-17))
* Extend the data saving service to save re-mapped Instances ([MODMARCMIG-19](https://folio-org.atlassian.net/browse/MODMARCMIG-19))
* Provide necessary module permission to call bulk instances upsert endpoint ([MODMARCMIG-23](https://folio-org.atlassian.net/browse/MODMARCMIG-23))
* Ensure marc-bib mapping rules usage when running marc migration for instances ([MODMARCMIG-31](https://folio-org.atlassian.net/browse/MODMARCMIG-31))

### Tech Dept
* Update documentation ([MODMARCMIG-16](https://issues.folio.org/browse/MODMARCMIG-16))
* Rename module permissions to call mapping-metadata endpoint ([MODMARCMIG-30](https://folio-org.atlassian.net/browse/MODMARCMIG-30))
