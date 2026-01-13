# **Deep Dive: Apache Kafka in Sustainable Energy Optimization Platform**

This tutorial explores the implementation of **Apache Kafka** as the event-streaming service of the platform. In this architecture, Kafka serves as the asynchronous bridge between data ingestion (Billing Service) and recommendation generation (Recommendation Service).

## **Kafka's role in the platform**
In a traditional synchronous system, the Billing Service would call the Recommendation Service via a REST API. If the Recommendation Service was slow or down, the user's data ingestion would fail.

**By using Kafka, we achieve:**
- **Decoupling**: The Billing Service doesn't care if the Recommendation Service is busy; it just "emits" the event.
- **Reliability**: Events are stored in the Kafka cluster until the consumer is ready to process them.
- **Ordering**: By using the *propertyId* as the message key, consumption ingestions for the same address are processed in the exact order they occurred.

## **Kafka Topic**
Platform uses one **Kafka Topic** named: `raw-consumption-events`.
A topic as a high-speed, persistent log file:
- **Producer (Billing)**: Appends a new event to the end of the log.
- **Consumer (Recommendation)**: Reads from the log at its own pace, keeping track of its "offset" (the last message it read).

## **Project Dependencies**
To enable Kafka in Spring Boot, I added the following dependency in both the billing-service and recommendation-service pom.xml files.
```
<dependency>
    <groupId>org.springframework.kafka</groupId>
    <artifactId>spring-kafka</artifactId>
</dependency>
```

## **KafkaProducerService class: Billing Service**
The **KafkaProducerService** class is responsible for taking a ConsumptionRecord and publishing it to the Kafka cluster.  
! The core engine for sending data is the **KafkaTemplate**.


