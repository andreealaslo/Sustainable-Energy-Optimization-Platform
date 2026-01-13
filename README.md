# Sustainable-Energy-Optimization-Platform

## **Overview**

The platform is a fully containerized, microservice-based system designed to monitor energy consumption, calculate environmental impact (carbon scores) and provide smart recommendations for energy optimization.

The system leverages a robust backend built with <ins>Java Spring Boot</ins>, secured <ins>REST</ins> endpoints, a microfrontend architecture using <ins>React</ins> and <ins>Module Federation</ins> and an <ins>Nginx API Gateway</ins> for centralized security and request routing. Event streaming is made by <ins>Kafka</ins>, asynchronous communication is handled via <ins>RabbitMQ</ins>, while real-time user alerts are pushed through <ins>WebSockets</ins>. Security is enforced via <ins>JWT-based authentication</ins>. Database used is <ins>PostgreSQL</ins>, shared across the microservices. The carbon score is calculated using a function build with <ins>OpenFaas</ins>. All the backend services are in a <ins>Docket<ins> container. 

## **Key Features**

- **Microservice Architecture**: Decoupled services for users, billing, recommendations, and notifications.
- **Microfrontend Design**: Modular UI using React Module Federation (Shell and Dashboard).
- **Event Streaming**: Kafka handles the consumption record ingestion streaming. Decoupled communication between billing and recommendations.
- **Asynchronous Messaging**: RabbitMQ handles the recommendation generation, consuming the event from Kafka.
- **Serverless Computing**: Python-based FaaS for isolated carbon footprint calculations.
- **Real-Time Notifications**: WebSocket integration for instant "High Usage" alerts, consumed from RabbitMQ.
- **Containerization**: Full deployment via Docker and Docker Compose.

## **Application Components**

### **Backend Services**

- **User Service (Port 8081)**: Manages user registration, property ownership, and JWT issuance.
- **Billing Service (Port 8082)**: Processes raw energy consumption data (kWh) and acts as a Kafka Producer, emitting consumption events.
- **Recommendation Service (Port 8083)**:  Acts as a Kafka Consumer, analyzing consumption events to calculate carbon scores via FaaS and providing smart recommendations.
- **Notification Service (Port 8084)**: Acts as a bridge between RabbitMQ and the frontend, pushing real-time alerts via WebSockets.

### **API Gateway (Port 8080)**
Built with Spring Cloud Gateway, it serves as the single entry point. It implements a Global CORS Filter and a JwtAuthenticationFilter that:
- Validates the Bearer Token.
- Extracts the User ID.
- Injects *X-Auth-User-Id* into the request headers for downstream services.

### **Database**
A shared **PostgreSQL** instance is used across microservices. Each service manages its own logical tables (Users, Properties, Consumption Records, Recommendations) to ensure data ownership and service independence.

### **Microfrontend Architecture**
The frontend is built as a Microfrontend Architecture using Webpack Module Federation:
- [Frontend GitHub Repo](https://github.com/andreealaslo/Sustainable-Energy-Optimization-Platform-Frontend)
- **Shell App (Host)**: Manages the main layout, user session, WebSocket connection, and global notifications.
- **Dashboard App (Remote)**: An isolated component that provides energy visualizations (Recharts) and data entry forms.



## **Event Streaming and Asynchronous Alerts - Details**

### **Apache Kafka (Event Streaming)**
Kafka handles the high-volume, "real-time" data flow within the system:
- **Workflow**: When the Billing Service receives an ingestion request, it saves the record and publishes a *raw-consumption-events* message to Kafka.
- **Consumer**: The Recommendation Service consumes these events asynchronously to begin the carbon score calculation (FaaS) process without slowing down the ingestion API.

### **RabbitMQ (Asynchronous Alerts)**
RabbitMQ handles the "Alerting" pipeline:
- **Workflow**: If the Recommendation Service determines that a carbon score calculated by FaaS has breached a critical threshold (>20, hardcoded value), it publishes an alert to the *alert-queue*.
- **Delivery**: The Notification Service picks up this alert and pushes it to the React Shell via WebSockets.

## **Detailed View of Microservices**
### **1. User Service**
Responsible for identity and assets.
- Public: `GET /users/health` - Health Check.
- Public: `POST /users/register` – Create account.
- Public: `POST /users/login` – Authenticate and receive JWT.
- Secured REST: `GET /api/users/profile` – Returns user entity.
- Secured REST: `POST /api/users/register-property` - Register a user's property (usually an address).
- Secured REST: `GET /api/users/properties` – List all properties linked to the authenticated user.

![Alt text](../assets/images/User Service Diagram.png)
  
### **2. Billing  Service**
The producer of the event stream.
- Public: `GET /billing/health` - Health Check.
- Secured REST: `POST /api/billing/ingest` – Submit kWh usage. Data is saved locally and then streamed to Kafka.

### **3. Recommendation  Service**
The consumer and recommendation engine.
- Kafka Listener: Monitors raw-consumption-events.
- FaaS Integration: Calls the Python carbon-calculator function to compute metrics.
- Alert Trigger: Sends messages to RabbitMQ if usage is excessive.
- Public: `GET /recommendations/health` - Health Check.
- Secured REST: `POST /api/recommendations/property/{propertyID}` – Returns history recommendations for the property with the corresponding ID.
  
### **4. Notification  Service**
The real-time hub.
- Rabbit Listener: Monitors alert-queue.
- WebSocket Server: Broadcasts messages to /topic/notifications.
  
## **Deployment & Setup**
### **Prerequisites**:
- Docker & Docker Compose
- Java 17 (Temurin) & Maven
- Node.js & npm

### **Execution**:
1. Build: `mvn clean package -DskipTests`
2. Deploy: `docker compose up -d --build`
3. Access:
   - API Gateway: `http://localhost:8080`
   - RabbitMQ UI: `http://localhost:15672 (guest/guest)`
   - Shell Frontend: `http://localhost:3000`
   - Dashboard Remote Frontend: `http://localhost:3001`

## **Technologies Used**
- **Backend**: Java Spring Boot, Spring Cloud Gateway
- **Frontend**: React, Module Federation, Tailwind CSS
- **Event Streaming**: Apache Kafka
- **Message Broker**: RabbitMQ
- **Faa**S: Python (classic-watchdog)
- **Notifications**: WebSocket
- **Database**: PostgreSQL
- **Security**: JWT Authentication.
  
