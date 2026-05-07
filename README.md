# Conditioning Service (osm-cond)

Microservice for managing conditioning processes and product statuses.

## 📖 Functional Overview
Focuses on the final stages of the production chain: preparing the product for the market.

### Key Features
- **Conditioning Control**: Manages the bottling, labeling, and sealing process.
- **Product Status Tracking**: Tracks which batches are "Ready for Sale" versus "In Progress."
- **Lot Validation**: Final validation of lot numbers and expiration dates before shipment.


## 🛠 Tech Stack
- **Java 21**
- **Spring Boot 3.4.4**
- **PostgreSQL** (`osmoc`)
- **Discovery:** Netflix Eureka

## 🚀 Getting Started
```bash
./mvnw spring-boot:run
```

## ⚙️ Configuration
| Variable | Default | Description |
| :--- | :--- | :--- |
| `SERVER_PORT` | `8089` | Service Port |
| `DB_URL` | `jdbc:postgresql://localhost:5432/osmoc` | Database URL |
