# Kafka Orders Spring Boot Application

A Spring Boot application that demonstrates producing and consuming messages from a Kafka topic named "orders".

## Features

- **Kafka Producer**: REST API endpoint to create and send orders to Kafka
- **Kafka Consumer**: Automatically consumes orders from the Kafka topic
- **Order Model**: Structured order data with order ID, customer ID, product details, etc.
- **SCRAM-SHA-512 Support**: Configured to work with Strimzi Kafka on OpenShift

## Prerequisites

- Java 17 or higher
- Maven 3.6 or higher
- Kafka cluster (local or OpenShift/Strimzi)

## Project Structure

```
.
├── pom.xml
├── src/
│   ├── main/
│   │   ├── java/
│   │   │   └── com/example/kafka/
│   │   │       ├── KafkaOrdersApplication.java
│   │   │       ├── config/
│   │   │       │   └── KafkaConfig.java
│   │   │       ├── controller/
│   │   │       │   └── OrderController.java
│   │   │       ├── consumer/
│   │   │       │   └── OrderConsumer.java
│   │   │       ├── model/
│   │   │       │   └── Order.java
│   │   │       └── producer/
│   │   │           └── OrderProducer.java
│   │   └── resources/
│   │       ├── application.properties
│   │       ├── application-dev.properties
│   │       └── application-prod.properties
└── README.md
```

## Configuration

### Local Development

For local development without authentication, use `application-dev.properties`:

```properties
spring.kafka.bootstrap-servers=localhost:9092
```

Run with:
```bash
mvn spring-boot:run -Dspring-boot.run.profiles=dev
```

### OpenShift/Strimzi with SCRAM-SHA-512

For production with OpenShift/Strimzi Kafka, update `application-prod.properties`:

1. **Get Kafka Broker URL**: 
   ```bash
   oc get routes -n kafka | grep kafka-bootstrap
   ```

2. **Get Kafka User Password**:
   ```bash
   oc get secret test-app-client -n kafka -o jsonpath='{.data.password}' | base64 -d
   ```

3. **Update application-prod.properties**:
   ```properties
   spring.kafka.bootstrap-servers=<broker-url>:9094
   spring.kafka.security.protocol=SASL_SSL
   spring.kafka.properties.sasl.mechanism=SCRAM-SHA-512
   spring.kafka.properties.sasl.jaas.config=org.apache.kafka.common.security.scram.ScramLoginModule required username="test-app-client" password="<your-password>";
   ```

Run with:
```bash
mvn spring-boot:run -Dspring-boot.run.profiles=prod
```

## Building and Running

### Build the application:
```bash
mvn clean package
```

### Run the application:
```bash
java -jar target/kafka-orders-app-1.0.0.jar
```

Or with Maven:
```bash
mvn spring-boot:run
```

The application will start on port 8080.

## Usage

### Create an Order

Send a POST request to create and publish an order:

```bash
curl -X POST http://localhost:8080/api/orders \
  -H "Content-Type: application/json" \
  -d '{
    "customerId": "customer-123",
    "productName": "Laptop",
    "quantity": 1,
    "price": 999.99,
    "status": "PENDING"
  }'
```

### Sample Order JSON

```json
{
  "orderId": "order-123",
  "customerId": "customer-456",
  "productName": "Smartphone",
  "quantity": 2,
  "price": 699.99,
  "orderDate": "2024-01-15 10:30:00",
  "status": "PENDING"
}
```

### Health Check

```bash
curl http://localhost:8080/api/orders/health
```

## How It Works

1. **Producer**: The `OrderController` receives HTTP requests and uses `OrderProducer` to send orders to the Kafka topic "orders"
2. **Consumer**: The `OrderConsumer` automatically listens to the "orders" topic and processes incoming messages
3. **Consumer Group**: Uses "app-consumer-group" as configured in the KafkaUser ACLs

## Kafka Topic Configuration

The application expects a Kafka topic named "orders" with:
- 3 partitions (as configured in `openshift/topic-orders.yaml`)
- Consumer group: `app-consumer-group`
- User: `test-app-client` (with SCRAM-SHA-512 authentication)

## Logs

The application logs:
- Producer: Success/failure of sending messages to Kafka
- Consumer: Details of received messages including partition and offset

## Troubleshooting

1. **Connection Issues**: Verify Kafka broker URL and network connectivity
2. **Authentication Errors**: Check username and password in application properties
3. **Topic Not Found**: Ensure the "orders" topic exists in Kafka
4. **Permission Denied**: Verify the KafkaUser has proper ACLs (Read, Write, Describe on "orders" topic)

