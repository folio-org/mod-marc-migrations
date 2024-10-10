## v1.0.0 In progress
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
* implement endpoint to create new migration operation ([MODMARCMIG-5](https://issues.folio.org/browse/MODMARCMIG-5))
* implement endpoint to get migration operation by ID ([MODMARCMIG-8](https://issues.folio.org/browse/MODMARCMIG-8))
* implement async chunks preparation on migration operation creation ([MODMARCMIG-6](https://issues.folio.org/browse/MODMARCMIG-6))
* implement async records mapping mechanism for MARC migration (Process Chunks) ([MODMARCMIG-7](https://issues.folio.org/browse/MODMARCMIG-7))
* implement async mapped records saving mechanism for MARC migration (Process Chunks) ([MODMARCMIG-9](https://issues.folio.org/browse/MODMARCMIG-9))
* update documentation ([MODMARCMIG-16](https://issues.folio.org/browse/MODMARCMIG-16))
* prepare chunks of MARC Bibs for mapping ([MODMARCMIG-17](https://folio-org.atlassian.net/browse/MODMARCMIG-17))
* extend the data saving service to save re-mapped Instances ([MODMARCMIG-19](https://folio-org.atlassian.net/browse/MODMARCMIG-19))
* provide necessary module permission to call bulk instances upsert endpoint ([MODMARCMIG-23](https://folio-org.atlassian.net/browse/MODMARCMIG-23))
* rename module permissions to call mapping-metadata endpoint ([MODMARCMIG-30](https://folio-org.atlassian.net/browse/MODMARCMIG-30))
