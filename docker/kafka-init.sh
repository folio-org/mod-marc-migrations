#!/bin/bash

# Wait for Kafka to be ready
sleep 10

# Create Kafka topics
KAFKA_BROKER=${KAFKA_HOST}:${KAFKA_PORT}

echo "Creating Kafka topics for mod-marc-migrations..."

# Topics for mod-entities-links (which mod-marc-migrations depends on)
TOPICS=(
"${ENV}.Default.inventory.instance"
"${ENV}.Default.inventory.holdings-record"
"${ENV}.Default.inventory.item"
"${ENV}.Default.inventory.bound-with"
"${ENV}.Default.authorities.authority"
)

# Updated to use the full path to kafka-topics.sh
KAFKA_TOPICS_CMD="/opt/kafka/bin/kafka-topics.sh"

for TOPIC in "${TOPICS[@]}"; do
  $KAFKA_TOPICS_CMD \
    --create \
    --bootstrap-server "$KAFKA_BROKER" \
    --replication-factor 1 \
    --partitions "${KAFKA_TOPIC_PARTITIONS}" \
    --topic "$TOPIC" \
    --if-not-exists
  echo "Created topic: $TOPIC"
done

echo "Kafka topics created successfully."

