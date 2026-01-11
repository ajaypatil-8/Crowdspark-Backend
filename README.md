# CrowdSpark – Backend (Spring Boot)

CrowdSpark is a Kickstarter-like crowdfunding backend built using **Spring Boot**, designed with **real-world, production-grade architecture**.  
It supports **multi-role authentication**, **secure payments**, and **scalable domain design**, similar to platforms like **Kickstarter** and **Indiegogo**.

---

##  Tech Stack

- Java 21
- Spring Boot 4
- Spring Security (JWT + Refresh Tokens)
- Spring Data JPA (Hibernate)
- PostgreSQL
- ModelMapper
- JWT (Access & Refresh Tokens)
- Role-Based Access Control (RBAC)

---

## 👥 User Roles & Permissions

###  ADMIN
- Approve / reject projects
- Assign & manage categories
- Platform moderation

###  CREATOR
- Create crowdfunding projects
- Add project updates
- Manage project media & rewards

###  BACKER
- Donate to projects
- Earn rewards based on contribution

---
## Security Features

- Stateless JWT authentication
- Refresh tokens stored securely (hashed in DB)
- Role-based endpoint protection (`@PreAuthorize`)
- Payment idempotency handling
- Secure payment webhook verification
- Audit logging for critical actions

---

##  Core Features

- User registration & login
- Multi-role authorization system
- Project creation & approval workflow
- Category assignment & media management
- Nested comments system
- Donation & reward allocation
- Payment webhook processing
- Audit trail for sensitive operations

---

## Project Structure

```
src/main/java/Crowdspark/Crowdspark
├── controller
├── service
│   └── impl
├── repository
├── entity
│   └── type
├── dto
├── security
├── config
├── exception
└── util
```

---

##  Configuration

❌ Sensitive configuration files are **NOT committed** to GitHub.

The project uses **environment variables** for all secrets.

### ✅ Example configuration file (safe to commit)

`application-example.yml`

```yaml
spring:
  datasource:
    url: ${DB_URL}
    username: ${DB_USERNAME}
    password: ${DB_PASSWORD}

jwt:
  secret: ${JWT_SECRET}
  access:
    expiration: ${JWT_ACCESS_EXP}
  refresh:
    expiration: ${JWT_REFRESH_EXP}

payments:
  webhook:
    secret: ${PAYMENTS_WEBHOOK_SECRET}
```

---

##  API Testing

- APIs tested using **Postman**
- JWT auto-injected using Postman environments
- Role-based access tested for all endpoints (**ADMIN / CREATOR / BACKER**)
- Payment webhooks tested with **idempotency handling**

---

##  Notes

- This repository contains **backend code only**
- Frontend (React / Next.js) will be integrated separately
- Designed for learning + real-world backend architecture showcase

---

##  Author (Ajay Patil)

Built with ❤️ as a **full-stack learning + portfolio project**.
