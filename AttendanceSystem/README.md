# JJCET ERP — Portable Attendance System (Ubuntu + H2)

Attendance and academic ERP management system for JJ College of Engineering and Technology.

---

## 🚀 Quick Start (Zero-Config Local Development)

This repository is optimized for **Ubuntu Linux (26.04 LTS)** using **Java 21** and an embedded **H2 database** for instant, machine-independent local development.

### Requirements
- **OS**: Ubuntu Linux 26.04 LTS (or compatible Linux/POSIX OS)
- **JDK**: OpenJDK / Oracle Java JDK 21+
- **Build Tool**: Maven wrapper (`./mvnw` included)

### 1. Clone & Run Immediately
```bash
# Clone repository
git clone https://github.com/dhilipan182005/jjcet-attendance-erp.git
cd jjcet-attendance-erp

# Run application using embedded H2 database (No PostgreSQL setup required!)
./mvnw spring-boot:run
```

The application starts on `http://localhost:10000` with the **`local`** profile active by default.

---

## 🔐 Default Credentials & H2 Console

### Default Bootstrap Administrator
When running in the `local` profile, an administrator account is initialized automatically on first startup:
- **User ID**: `ADMIN001`
- **Password**: `Admin@123`
- **Role**: `ADMIN`

### H2 Database Console
Access the in-memory H2 database directly in your browser:
- **URL**: `http://localhost:10000/h2-console`
- **JDBC URL**: `jdbc:h2:mem:testdb`
- **Driver Class**: `org.h2.Driver`
- **User Name**: `sa`
- **Password**: *(leave blank)*

---

## 🛠️ Technology Stack

- **Backend Framework**: Spring Boot 4 / 3 (Java 21)
- **Security**: Spring Security (JWT-based stateless authentication & role authorization)
- **Local Database**: Embedded H2 (`jdbc:h2:mem:testdb;MODE=PostgreSQL`)
- **Production Database**: PostgreSQL with Flyway migrations
- **Frontend**: Vanilla HTML5, CSS3, JavaScript (ES modules, hash routing)

---

## 📦 Build & Test Commands

```bash
# Clean & package executable JAR
./mvnw clean package

# Run unit & integration test suite
./mvnw test

# Run with explicit local profile
./mvnw spring-boot:run -Dspring-boot.run.profiles=local

# Run with production profile (requires PostgreSQL & environment variables)
SPRING_PROFILES_ACTIVE=prod ./mvnw spring-boot:run
```

---

## 📂 Project Structure

```
jjcet-attendance-erp/
├── frontend/                     # Web UI static assets (HTML, CSS, JS)
│   ├── admin-dashboard.html     # Administrator panel
│   ├── teacher-dashboard.html   # Faculty panel
│   ├── login.html               # Authentication page
│   ├── css/                     # Application styling
│   └── js/                      # Modular client logic (API, Auth, UI)
├── src/
│   ├── main/
│   │   ├── java/com/example/attendancesystem/
│   │   │   ├── config/          # Security, Cors, App, H2, AdminBootstrap
│   │   │   ├── controller/      # REST API Endpoints
│   │   │   ├── dto/             # Data Transfer Objects & Requests/Responses
│   │   │   ├── entity/          # JPA Domain Entities
│   │   │   ├── repository/      # Spring Data Repositories
│   │   │   ├── security/        # JWT Filter, UserDetailsService, RateLimiting
│   │   │   └── service/         # Business Logic Services
│   │   └── resources/
│   │       ├── application.properties         # Master configuration defaults
│   │       ├── application-local.properties   # Local H2 development profile
│   │       └── application-prod.properties    # Production PostgreSQL profile
│   └── test/                    # Suite of automated tests
├── pom.xml                       # Maven dependencies & Java 21 config
└── README.md                     # Project documentation
```

---

## 🌐 Production Deployment

For production deployments (e.g., Render + Supabase PostgreSQL):

Set the following environment variables:
- `SPRING_PROFILES_ACTIVE`: `prod`
- `DATABASE_URL`: `jdbc:postgresql://<HOST>:<PORT>/<DATABASE>`
- `DATABASE_USERNAME`: Database username
- `DATABASE_PASSWORD`: Database password
- `JWT_SECRET`: Minimum 32-character secret key
- `DEFAULT_ADMIN_ID`: Custom admin ID (optional)
- `DEFAULT_ADMIN_PASSWORD`: Custom admin password (optional)
