# 🐳 Docker Compose Setup for mod-marc-migrations

Local development environment for mod-marc-migrations using Docker Compose.

## 📋 Prerequisites

- Docker and Docker Compose V2+
- Java 21+ (for local development mode)
- Maven 3.8+ (for building the module)

## 🏗️ Architecture

Two compose files provide flexible development workflows:

- **`infra-docker-compose.yml`**: Infrastructure services only (PostgreSQL, Kafka, MinIO, etc.)
- **`app-docker-compose.yml`**: Full stack including the module (uses `include` to incorporate infra services)

> **Note about Kafka**: mod-marc-migrations itself does not use Kafka. Kafka and Zookeeper are included because the **mod-entities-links** dependency service requires them. If you don't need mod-entities-links for your development work, you can comment out the Kafka, Zookeeper, Kafka UI, and mod-entities-links services from the compose files.

## ⚙️ Configuration

Configuration is managed via the `.env` file in this directory.

### Key Environment Variables

| Variable | Description | Default |
|----------|-------------|---------|
| `ENV` | FOLIO environment name | `dev` |
| `MODULE_REPLICAS` | Number of module instances | `1` |
| `DB_HOST` | PostgreSQL hostname | `postgres` |
| `DB_PORT` | PostgreSQL port | `5432` |
| `DB_DATABASE` | Database name | `okapi_modules` |
| `DB_USERNAME` | Database user | `folio_admin` |
| `DB_PASSWORD` | Database password | `folio_admin` |
| `KAFKA_HOST` | Kafka hostname | `kafka` |
| `KAFKA_PORT` | Kafka port (host access) | `29092` |
| `S3_URL` | MinIO/S3 endpoint URL | `http://minio:9000/` |
| `S3_BUCKET` | S3 bucket name | `marc-migrations` |
| `S3_ACCESS_KEY_ID` | S3 access key | `access` |
| `S3_SECRET_ACCESS_KEY` | S3 secret key | `secret12` |
| `RECORDS_CHUNK_SIZE` | Records processing chunk size | `10` |

## 🚀 Services

### PostgreSQL
- **Purpose**: Primary database for module data
- **Version**: PostgreSQL 16 Alpine
- **Access**: localhost:5432 (configurable via `DB_PORT`)
- **Credentials**: See `DB_USERNAME` and `DB_PASSWORD` in `.env`
- **Database**: See `DB_DATABASE` in `.env`
- **Init Scripts**: Auto-loads SQL scripts for mod-entities-links and mod-source-record-storage

### pgAdmin
- **Purpose**: Database administration interface
- **Access**: http://localhost:5000 (configurable via `PGADMIN_PORT`)
- **Login**: Use `PGADMIN_DEFAULT_EMAIL` and `PGADMIN_DEFAULT_PASSWORD` from `.env`

### Apache Kafka (with Zookeeper)
- **Purpose**: Message broker for event-driven architecture
- **Mode**: Zookeeper-based cluster
- **Listeners**:
  - Docker internal: `kafka:9092`
  - Host: `localhost:29092`

### Kafka UI
- **Purpose**: Web interface for Kafka management
- **Access**: http://localhost:8080 (configurable via `KAFKA_UI_PORT`)
- **Features**: Topic browsing, message viewing/producing, consumer group monitoring

### MinIO
- **Purpose**: S3-compatible object storage for MARC files
- **Access**: 
  - API: http://localhost:9000
  - Console: http://localhost:9001
- **Credentials**: See `S3_ACCESS_KEY_ID` and `S3_SECRET_ACCESS_KEY` in `.env`

### WireMock
- **Purpose**: Mock Okapi and other FOLIO modules for testing
- **Access**: http://localhost:9130 (configurable via `WIREMOCK_PORT`)
- **Mappings**: Located in `src/test/resources/mappings`

### mod-entities-links
- **Purpose**: Required dependency for MARC record linking
- **Access**: http://localhost:8083
- **Image**: folioci/mod-entities-links:latest
- **Note**: This service requires Kafka (which is why Kafka is included in the infrastructure)

## 📖 Usage

> **Note**: All commands in this guide assume you are in the `docker/` directory. If you're at the project root, run `cd docker` first.

### Starting the Environment

```bash
# Build the module first
mvn -f ../pom.xml clean package -DskipTests

# Start all services (infrastructure + module)
docker compose -f app-docker-compose.yml up -d
```

```bash
# Start only infrastructure services (for local development)
docker compose -f infra-docker-compose.yml up -d
```

```bash
# Start with build (if module code changed)
docker compose -f app-docker-compose.yml up -d --build
```

```bash
# Start specific service
docker compose -f infra-docker-compose.yml up -d postgres
```

### Stopping the Environment

```bash
# Stop all services
docker compose -f app-docker-compose.yml down
```

```bash
# Stop infra services only
docker compose -f infra-docker-compose.yml down
```

```bash
# Stop and remove volumes (clean slate)
docker compose -f app-docker-compose.yml down -v
```

### Viewing Logs

```bash
# All services
docker compose -f app-docker-compose.yml logs
```

```bash
# Specific service
docker compose -f app-docker-compose.yml logs mod-marc-migrations
```

```bash
# Follow logs in real-time
docker compose -f app-docker-compose.yml logs -f mod-marc-migrations
```

```bash
# Last 100 lines
docker compose -f app-docker-compose.yml logs --tail=100 mod-marc-migrations
```

### Scaling the Module

```bash
# Scale to 3 instances
docker compose -f app-docker-compose.yml up -d --scale mod-marc-migrations=3
```

```bash
# Or modify MODULE_REPLICAS in .env and restart
echo "MODULE_REPLICAS=3" >> .env
docker compose -f app-docker-compose.yml up -d
```

### Cleanup and Reset

```bash
# Complete cleanup (stops containers, removes volumes)
docker compose -f app-docker-compose.yml down -v
```

```bash
# Remove all Docker resources
docker compose -f app-docker-compose.yml down -v
docker volume prune -f
docker network prune -f
```

## 🔧 Development Workflows

### Workflow 1: Full Docker Stack
Run everything in Docker, including the module.

```bash
# Build the module
mvn -f ../pom.xml clean package -DskipTests

# Start all services
docker compose -f app-docker-compose.yml up -d

# View logs
docker compose -f app-docker-compose.yml logs -f mod-marc-migrations
```

**Use Case**: Testing the full deployment, simulating production environment, scaling tests.

### Workflow 2: Infrastructure Only + IDE
Run infrastructure in Docker, develop the module in your IDE.

```bash
# Start infrastructure
docker compose -f infra-docker-compose.yml up -d

# Run module from IDE or command line
mvn -f ../pom.xml spring-boot:run
```

**Use Case**: Active development with hot reload, debugging in IDE, faster iteration cycles.

### Workflow 3: Spring Boot Docker Compose Integration
Let Spring Boot manage Docker Compose automatically (recommended for rapid development).

```bash
# Run with dev profile (starts infrastructure automatically)
mvn -f ../pom.xml spring-boot:run -Dspring-boot.run.profiles=dev
```

The `dev` profile is configured to:
- Start services from `docker/infra-docker-compose.yml`
- Connect to services via localhost ports (Kafka: 29092, PostgreSQL: 5432, MinIO: 9000)
- Keep containers running after the application stops for faster subsequent startups

**Use Case**: Quickest way to start development, automatic infrastructure management, no manual Docker commands needed.

### Workflow 4: Spring Boot DevTools
For rapid development with automatic restart on code changes.

```bash
# Start infrastructure
docker compose -f infra-docker-compose.yml up -d

# Run with devtools (automatic restart on code changes)
mvn -f ../pom.xml spring-boot:run

# Make code changes - application will automatically restart
```

**Use Case**: Continuous development with automatic reload, live code updates, rapid feedback loop.

## 🛠️ Common Tasks

### Building the Module

```bash
# Clean build (skip tests)
mvn -f ../pom.xml clean package -DskipTests
```

```bash
# Build with tests
mvn -f ../pom.xml clean package
```

### Accessing Services

```bash
# PostgreSQL CLI
docker compose -f infra-docker-compose.yml exec postgres psql -U folio_admin -d okapi_modules
```

```bash
# View database tables
docker compose -f infra-docker-compose.yml exec postgres psql -U folio_admin -d okapi_modules -c "\dt"
```

```bash
# Check PostgreSQL health
docker compose -f infra-docker-compose.yml exec postgres pg_isready -U folio_admin
```

```bash
# List Kafka topics
docker compose -f infra-docker-compose.yml exec kafka kafka-topics.sh --bootstrap-server localhost:9092 --list
```

```bash
# Access MinIO Console
# Open browser to http://localhost:9001
# Login with S3_ACCESS_KEY_ID and S3_SECRET_ACCESS_KEY from .env
```

### Creating MinIO Bucket

```bash
# Using MinIO client
docker run --network docker_mod-marc-migrations-local --rm -it minio/mc alias set myminio http://minio:9000 access secret12
docker run --network docker_mod-marc-migrations-local --rm -it minio/mc mb myminio/marc-migrations
```

Or use the MinIO Console at http://localhost:9001

### Adding New Kafka Topics

Edit `kafka-init.sh` and add topics to the `TOPICS` array:

```bash
TOPICS=(
  "${ENV}.Default.inventory.instance"
  "${ENV}.Default.your-new-topic"  # Add your new topic here
)
```

After editing, restart the kafka-topic-init service:
```bash
docker compose -f infra-docker-compose.yml up -d kafka-topic-init
```

## 🐛 Troubleshooting

### Module won't start
- Check if the JAR is built: `ls -lh ../target/*.jar` (from docker directory)
- Check module logs: `docker compose -f app-docker-compose.yml logs mod-marc-migrations`
- Verify database is ready: `docker compose -f infra-docker-compose.yml exec postgres pg_isready`

### Database connection issues
- Verify PostgreSQL is running: `docker compose -f infra-docker-compose.yml ps postgres`
- Check database credentials in `.env`
- Test connection: `docker compose -f infra-docker-compose.yml exec postgres psql -U folio_admin -d okapi_modules -c "SELECT 1"`

### Kafka issues
- Check Kafka logs: `docker compose -f infra-docker-compose.yml logs kafka`
- Verify Kafka is ready: Use Kafka UI at http://localhost:8080
- Check Zookeeper: `docker compose -f infra-docker-compose.yml logs zookeeper`

### MinIO/S3 issues
- Check MinIO health: `curl http://localhost:9000/minio/health/live`
- View MinIO logs: `docker compose -f infra-docker-compose.yml logs minio`
- Verify bucket exists via MinIO Console at http://localhost:9001

### Port conflicts
- Check if ports are already in use: `lsof -i :5432 -i :8081 -i :9000 -i :29092`
- Modify ports in `.env` file if needed

### Container keeps restarting
- Check container logs: `docker compose -f app-docker-compose.yml logs <service-name>`
- Check health status: `docker compose -f app-docker-compose.yml ps`
- Verify dependencies are healthy before starting dependent services

### Clean slate restart
If things are broken, reset everything:

```bash

# Stop and remove everything
docker compose -f app-docker-compose.yml down -v
docker compose -f infra-docker-compose.yml down -v

# Clean up Docker resources
docker volume prune -f
docker network prune -f

# Start fresh
docker compose -f infra-docker-compose.yml up -d
```

## 🎯 Performance Tuning

### Database Optimization
Adjust PostgreSQL settings in `.env` or `infra-docker-compose.yml`:
```yaml
environment:
  POSTGRES_SHARED_BUFFERS: 256MB
  POSTGRES_WORK_MEM: 10MB
  POSTGRES_MAX_CONNECTIONS: 100
```

### Module Resource Limits
Adjust resource limits in `app-docker-compose.yml` under `deploy.resources`:
```yaml
resources:
  limits:
    cpus: "1.0"
    memory: "1G"
  reservations:
    cpus: "0.5"
    memory: "512M"
```

### Chunk Processing Configuration
Adjust processing parameters in `.env`:
```bash
RECORDS_CHUNK_SIZE=500
CHUNK_FETCH_IDS_COUNT=500
CHUNK_PERSIST_COUNT=1000
```

## 📚 Additional Resources

- [Docker Compose Documentation](https://docs.docker.com/compose/)
- [Spring Boot Docker Compose Support](https://docs.spring.io/spring-boot/reference/features/dev-services.html#features.dev-services.docker-compose)
- [Apache Kafka Documentation](https://kafka.apache.org/documentation/)
- [PostgreSQL Documentation](https://www.postgresql.org/docs/)
- [MinIO Documentation](https://min.io/docs/)

## 💡 Tips

1. **Use Spring Boot Docker Compose Integration** for the fastest development experience
2. **Keep infrastructure running** between development sessions to save startup time
3. **Use pgAdmin** to inspect database state and debug data issues
4. **Use Kafka UI** to monitor message flow and debug event processing
5. **Scale module instances** to test concurrent processing and load distribution
6. **Use MinIO Console** to inspect S3 storage and uploaded MARC files

## 🔌 Optional Services

### Running Without Kafka

If you don't need **mod-entities-links** for your development work, you can run without Kafka to save resources:

1. Comment out these services in both `infra-docker-compose.yml` and `app-docker-compose.yml`:
   - `kafka`
   - `kafka-ui`
   - `mod-entities-links`

2. Remove `kafka` and `mod-entities-links` from the `depends_on` section of the `mod-marc-migrations` service in `app-docker-compose.yml`

3. Start with the minimal setup:
```bash

# Only PostgreSQL, MinIO, and WireMock
docker compose -f infra-docker-compose.yml up -d postgres pgadmin minio api-mock
```

**Note**: mod-marc-migrations core functionality (MARC migrations) does not require Kafka. Kafka is only needed if you're testing features that interact with mod-entities-links.

