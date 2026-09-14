# Tarikh - Law ERP Software

## Overview

**Tarikh** is a comprehensive, multi-tenant Enterprise Resource Planning (ERP) system designed specifically for law firms in Nepal. Built with Java Spring Boot and a modern tech stack, Tarikh streamlines legal operations, case management, billing, and document workflows into a single unified platform.

The software is named **Tarikh** (तारिख), meaning "date" or "chronicle" in Nepali — reflecting its core purpose of organizing and tracking legal matters over time.

---

## Technology Stack

| Component | Technology |
|-----------|------------|
| Backend Framework | Spring Boot 4.0.6 |
| Language | Java 21 |
| Database | PostgreSQL (runtime), H2 (testing) |
| ORM | Spring Data JPA + MyBatis |
| Security | Spring Security + JWT Authentication |
| PDF Generation | OpenHTMLtoPDF |
| Web Scraping | Jsoup |
| API Documentation | Springdoc OpenAPI (Swagger UI) |
| Templating | Thymeleaf |
| Caching | Ehcache |
| Build Tool | Maven |

---

## Core Features

### 1. Authentication & Authorization

- **JWT-based Authentication** — Stateless token-based login system with secure JWT tokens (using JJWT library)
- **Multi-Factor Authentication (MFA)** — TOTP-based two-factor authentication support
- **Role-Based Access Control (RBAC)** — Granular permission system with roles, permissions, and modules
- **Subdomain-based Multi-Tenancy** — Each law firm gets its own subdomain for isolated access
- **Firm Context Switching** — Seamless switching between firms in multi-firm environments
- **User Login History Tracking** — Audit trail of all user login attempts with status tracking
- **Custom UserDetailsService** — Flexible user authentication integration

### 2. Firm Management

- **Firm Profiles** — Complete firm profile management including details, branding, and configuration
- **Firm Module Configuration** — Toggle and configure which modules are available per firm
- **Firm Email Configuration** — SMTP and email settings per firm
- **Employee Management** — Track firm employees with profiles and assignment management
- **Client Profile Management** — Maintain client contact information and profiles
- **Firm Types & Status Tracking** — Categorize firms by type and track their operational status

### 3. Case Management (Matter Management)

- **Matter (Case) Management** — Create, update, and track legal matters with full lifecycle support
- **Matter Number Generation** — Auto-generated, sequential matter numbering system
- **Matter Parties** — Track plaintiffs, defendants, and other parties involved in each case
- **Party Roles & Relationships** — Define roles (plaintiff, defendant, witness, etc.) and relationships between parties
- **Party Matching Service** — Smart matching to prevent duplicate party entries
- **Matter Timeline** — Chronological event tracking for every case with timeline events
- **Matter Status Tracking** — Track matter progress through defined statuses
- **Matter Types** — Categorize matters by type (civil, criminal, corporate, etc.)
- **Stale Matter Detection** — Identify and flag inactive or stagnant cases
- **Dashboard** — Comprehensive dashboard with case overviews, statistics, and key metrics
- **Calendar Integration** — Court event scheduling and calendar management
- **Case Assignment** — Assign cases to lawyers/attorneys with role-based assignment tracking
- **Court Case Management** — Track court cases with stages, status, and case roles
- **Court Event Management** — Schedule and manage court hearings/events
- **Court Event Status Tracking** — Track hearing statuses (scheduled, held, adjourned, etc.)
- **Court Case Stages** — Manage case progression through legal stages
- **Court Case Roles** — Define roles of participants in court cases
- **Court Levels** — Support for different court hierarchies (Supreme Court, High Court, District Court, etc.)
- **Court Case Reference Generation** — Auto-generated court case reference numbers
- **Appeal Deadline Engine** — Automated calculation of appeal deadlines based on configurable rules
- **Hearing Reminder Scheduler** — Automated scheduling of hearing reminders
- **Hearing Reminder Service** — Send reminders for upcoming court hearings via email
- **Hearing Reminder Log** — Track all reminder activities
- **Judgment Recording** — Record court judgments and outcomes
- **Outcome Tracking** — Track case outcomes with defined outcome types
- **Bail Status Tracking** — Track bail status for criminal cases
- **Upcoming Appeals** — Identify and track cases with approaching appeal deadlines

### 4. Court Hearing Scraper (Nepal Court Integration)

- **Daily Hearing Scraping** — Automated scraping of daily court hearing lists from Nepal court websites
- **Weekly Hearing Scraping** — Weekly hearing schedule extraction
- **Case Detail Parsing** — Parse detailed case information from court websites
- **Case Number Extraction** — Intelligent extraction of case numbers (both BS and internal formats)
- **Devanagari to Arabic Number Conversion** — Handle Nepali script numbers in scraped data
- **Nepali Date Utilities** — Full Nepal Bikram Samvat (BS) calendar conversion (BS to AD and AD to BS)
- **Court Website Client** — HTTP client for connecting to court websites
- **Hearing Match Tracking** — Match scraped hearings to firm cases
- **Hearing Status API** — API endpoint to check hearing status
- **Scraper Administration** — Admin interface to manage scraping configurations and monitor runs
- **Hearing Export Service** — Export scraped hearing data
- **Hearing Ingestion Service** — Process and ingest scraped hearing data into the system
- **Client Case Tracking** — Track external cases linked to firm matters
- **Devanagari Converter** — Convert between Devanagari and Arabic numeral systems

### 5. Invoice & Billing Management

- **Invoice Generation** — Create professional invoices for legal services
- **Invoice Items** — Line-item detail for invoices with multiple services/charges
- **Invoice Status Tracking** — Track invoice lifecycle (draft, sent, paid, overdue, etc.)
- **Invoice PDF Generation** — Generate professional PDF invoices using OpenHTMLtoPDF
- **Invoice PDF Service** — Dedicated service for styling and generating invoice PDFs
- **Invoice Updates** — Modify invoices and invoice items as needed
- **Invoice History** — Maintain complete invoice history per matter/client

### 6. Project Management

- **Project Management** — Create and manage legal projects alongside matters
- **Project Dashboard** — Overview of all projects with key metrics
- **Project Members** — Assign team members to projects with role-based access
- **Project Member Roles** — Define roles within projects (lead, member, viewer, etc.)
- **Project Status Tracking** — Track project progress and status
- **Project Credential Management** — Securely store and manage project-related credentials
- **Credential Service** — Add, update, and manage credentials securely
- **Client Portal** — Portal functionality for clients to access project information
- **Client Project Response** — Structured responses for client-facing project data
- **Renewal Management** — Track and manage license/contract renewals
- **Renewal Types** — Define different types of renewals
- **Renewal Recurrence** — Configure recurring renewal schedules
- **Renewal Instance Tracking** — Track individual renewal instances and their status

### 7. User Management

- **User Profile Management** — Full CRUD operations for user profiles
- **User Summary View** — Quick overview of all users in the system
- **Bulk User Operations** — Bulk deactivate users or bulk change user roles
- **Password Reset** — Secure password reset functionality
- **User Permissions View** — View and audit user permissions
- **Global Dashboard** — Organization-wide dashboard with aggregated metrics

### 8. Audit & Logging

- **Audit Trail** — Comprehensive audit logging of all system actions
- **Audit Aspect** — Aspect-oriented audit logging via Spring AOP
- **Async Audit Writer** — Non-blocking audit log writing for performance
- **Audit SPEL Helper** — Spring Expression Language helper for audit data extraction
- **Audit Log Entity** — Structured audit log storage
- **Audit Controller** — API for viewing and managing audit logs

### 9. Master Data Management

- **Country Management** — Reference data for countries
- **Province Management** — Nepal province reference data
- **District Management** — Nepal district reference data (all 77 districts)
- **Master Data Seeder** — Automatic seeding of master data on startup
- **Localization Support** — Message source service for internationalization

### 10. System Configuration

- **System Configuration Settings** — Centralized system-wide configuration
- **Config Encryption Utility** — Encrypt sensitive configuration values
- **Configuration Encryption** — Secure storage of API keys, secrets, and sensitive data

### 11. Email Services

- **Email Service** — Integrated email sending capabilities
- **Hearing Reminder Emails** — Automated email reminders for court hearings
- **Spring Mail Integration** — Uses Spring Boot's mail starter for SMTP integration

### 12. API & Integration

- **RESTful API** — Full REST API for all modules
- **Swagger/OpenAPI Documentation** — Auto-generated API documentation accessible via Swagger UI
- **API Request/Response DTOs** — Structured request and response objects
- **Paged Responses** — Pagination support for list endpoints
- **Global Exception Handling** — Centralized exception handling with consistent error responses
- **Permission Evaluator** — Programmatic permission checking
- **Firm Interceptor** — Intercepts requests for firm context resolution

---

## Multi-Tenancy Architecture

Tarikh is built as a **multi-tenant SaaS platform**:
- Each law firm operates on its own subdomain (e.g., `firm1.tarikh.com`, `firm2.tarikh.com`)
- Data isolation between tenants at the application level
- Firm-specific module enablement and configuration
- Tenant-aware authentication and authorization

---

## Security Features

- **JWT Token Authentication** with configurable expiration
- **Two-Factor Authentication (TOTP)** optional for users
- **RBAC with granular permissions** — Users, Roles, Permissions, Modules
- **Firm-level role management** — Roles scoped to individual firms
- **Security Configuration** — Spring Security-based filter chain
- **Custom Authorization Entry Point** — Handles authentication errors gracefully
- **Header-based firm context** — Subdomain extraction and firm resolution
- **Audit logging for security events**

---

## Nepali Legal System Specialization

Tarikh is purpose-built for **Nepal's legal environment**:

- **Nepali Date Support** — Full Bikram Samvat (BS) calendar conversion utilities
- **Devanagari Script Handling** — Parse and convert Nepali script text and numbers
- **Court Hierarchy Awareness** — Models Nepal's court structure (Supreme Court, High Courts, District Courts, etc.)
- **Court Hearing Scraping** — Automated data extraction from Nepal court websites
- **Nepal Geographic Data** — Built-in provinces and all 77 districts of Nepal
- **Bail Status Tracking** — Relevant for Nepal's criminal justice system
- **Appeal Deadline Engine** — Calculates deadlines per Nepal's legal framework
- **Nepali Court Case Stages** — Reflects actual stages in Nepali court proceedings

---

## Modules Summary

| Module | Description |
|--------|-------------|
| **Auth** | Authentication, JWT, MFA, login history |
| **RBAC** | Roles, permissions, modules, role-permission mapping |
| **Firm** | Firm profiles, employees, clients, email config, module config |
| **Case Management** | Matters, court cases, court events, parties, timelines, assignments, calendar |
| **Scraper** | Court hearing scraping, Nepali date conversion, case detail parsing |
| **Invoice** | Invoicing, PDF generation, billing line items |
| **Project Management** | Projects, members, credentials, renewals, client portal |
| **User Management** | User CRUD, bulk operations, permissions, global dashboard |
| **Audit** | Audit trail, async logging, audit aspects |
| **ME (My Profile)** | Personal user profile endpoint |
| **Master Data** | Countries, provinces, districts (Nepal-specific) |
| **Common** | System config, encryption, utilities, exceptions |
| **Super Admin** | Platform-wide administration |
| **Tenant** | Tenant type management |

---

## Use Cases

Tarikh is designed for:

1. **Law Firms in Nepal** — Managing cases, clients, billing, and court 일정에
2. **Solo Practitioners** — Individual lawyers managing their practice
3. **Large Law Firms** — Multi-user, multi-department legal practices
4. **Legal Consultancies** — Firms offering various legal services
5. **Corporate Legal Departments** — In-house legal teams tracking matters

---

## API Documentation

Once the application is running, API documentation is available at:
- **Swagger UI**: `/swagger-ui.html`
- **OpenAPI JSON**: `/v3/api-docs`

---

## Project Structure

```
com.lawfirm.erp
├── auth/          # Authentication & security
├── common/        # Shared utilities, exceptions, constants
├── config/        # Spring configuration classes
├── customer/      # Customer profile management
├── dto/           # Data Transfer Objects
├── encryption/    # Encryption utilities
├── entity/        # JPA entities (shared)
├── firm/          # Firm management module
├── modules/
│   ├── audit/         # Audit logging
│   ├── casemanagement/# Case/matter management
│   ├── email/         # Email services
│   ├── invoice/       # Invoice & billing
│   ├── me/           # User profile
│   ├── projectmanagement/ # Projects & renewals
│   ├── scraper/       # Court hearing scraper
│   └── usermanagement/# User management
├── rbac/          # Role-Based Access Control
├── superadmin/    # Platform administration
└── tenant/        # Multi-tenancy support
```

---

## SEO Keywords (for Nepal market)

For search engine optimization targeting Nepal, relevant keywords include:

- Law ERP Nepal
- Legal practice management software Nepal
- Case management system for Nepali lawyers
- Court case tracking software Nepal
- Law firm management system Nepal
- Nepal court hearing tracker
- Nepali date calendar software
- Bikram Samvat date converter
- Nepal Bikram Sambat calendar
- Legal billing software Nepal
- Invoice generator for lawyers Nepal
- Multi-tenant law ERP
- Nepal law firm automation
- Court hearing reminder Nepal
- Case number tracking Nepal
- Devanagari to Arabic converter
- Nepal court website scraper
- Lawyer practice management Nepal
- नेपाल ल importance leaving management software
- तारिख ल importance leaving ERP

---

*Document generated for Tarikh Law ERP — September 2026*
