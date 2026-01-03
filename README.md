# High-Concurrency E-commerce Flash Sale System

## Introduction

This project is a high-concurrency e-commerce seckill (flash sale) system designed to handle traffic spikes similar to "Black Friday" scenarios. It adopts a **Funnel Architecture** to filter invalid requests layer by layer, ensuring the database remains stable under high load.

**Key Achievements:**

* Boosted QPS (Queries Per Second) by **110%** (from 1.2k to 2.5k) through multi-level caching.
* Achieved **Zero Overselling** using Redis Lua scripts and atomic operations.
* Implemented **Traffic Peak Shaving** using RabbitMQ to protect the database.

---

## Architecture & Implementation Principles

The system addresses the core challenges of **High Concurrency**, **Overselling**, and **Malicious Attacks** using the following strategies:

### 1. Multi-Level Caching (Performance)

To minimize database hits, a 3-level caching strategy is implemented:

* **Client Side:** Browser caching for static resources (CSS/JS).
* **Application Layer (Redis Page Cache):** Manually rendered HTML pages are cached in Redis. Requests for hot item details return directly from Redis, skipping the template engine rendering process.
* **JVM Local Cache:** A local `HashMap` marks sold-out items to block requests before they even reach Redis.

### 2. Solution to Overselling (Concurrency)

* **Redis Pre-Reduction:** Inventory is pre-loaded into Redis.
* **Atomic Operations:** Utilized **Lua scripts** to execute the "Check Stock" and "Decrement Stock" operations atomically. This ensures thread safety without the performance cost of database locks.
* **Consistency:** The Redis inventory serves as the traffic filter. The database inventory serves as the final record, synchronized asynchronously.

### 3. Asynchronous Processing (Peak Shaving)

* **RabbitMQ Integration:** Instead of writing to the MySQL database synchronously, successful purchase requests are serialized and sent to a RabbitMQ queue.
* **Traffic Buffering:** The Message Queue acts as a buffer, allowing the database to consume orders at a stable rate (e.g., 500 TPS) regardless of the instant traffic spike (e.g., 10,000 QPS).

### 4. Security & Anti-Bot

* **Dynamic Hidden URLs:** The purchasing API URL is randomized (UUID) for each user/item pair to prevent script-based URL hammering.
* **Distributed Rate Limiting:** Limits access frequency per user (e.g., max 5 requests per 5 seconds) using Redis.
* **Mathematical CAPTCHA:** Adds a computational cost to the user flow to flatten traffic spikes.

### 5. Distributed Session

* Implemented a stateless session management system using **Redis + Cookie** (Token-based) to support horizontal scaling.

---

## Tech Stack

* **Core Framework:** Spring Boot
* **Middleware:** Redis (Caching/Locking), RabbitMQ (Messaging)
* **Database:** MySQL, MyBatis
* **Frontend:** Thymeleaf, Bootstrap, jQuery
* **Tools:** JMeter (Stress Testing), Maven

---

## Environment
* **JDK 1.8+**
* **Maven 3.x**
* **MySQL 5.7+**
* **Redis 3.x+**
* **RabbitMQ** 


## Performance Benchmark

Stress testing was conducted using **Apache JMeter** with the following configuration:

* **Threads:** 5000
* **Ramp-up Period:** 1s
* **Loop Count:** 10

**Results:**
| Metric | Direct DB Access | With Redis & MQ Optimization | Improvement |
| :--- | :--- | :--- | :--- |
| **QPS** | ~1,267 | **~2,884** | **+127%** |
| **Response Time** | High Latency | Low Latency | Significant Drop |
| **DB Load** | High (Risk of crash) | Stable | **Protected** |

---
