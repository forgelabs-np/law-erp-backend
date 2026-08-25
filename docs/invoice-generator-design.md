# Invoice Generator Module — Design Spec

**Date:** 2026-08-25
**Status:** Approved
**Scope:** Super Admin → Firm invoice generation with Thymeleaf PDF

---

## 1. Summary

A module that lets the Super Admin create, manage, and send invoices to law firms. Invoices have flexible line items, are rendered as professional PDFs via Thymeleaf + OpenHTMLtoPDF, and can be downloaded or emailed to the firm admin.

## 2. Why This Approach

**Thymeleaf + OpenHTMLtoPDF** over alternatives:
- We already use Thymeleaf in the project (Spring Boot default) — no new template engine
- OpenHTMLtoPDF supports full CSS (flexbox, grids, fonts, colors) — the invoice design lives in HTML/CSS, not in Java code
- Flying Saucer was considered but OpenHTMLtoPDF is more actively maintained and has better CSS3 support
- iText was rejected because it's AGPL licensed (license risk)

**Flexible line items** (admin types anything) over matter-linked billing:
- This is super admin → firm billing (platform fees), not client → firm billing
- Admin needs to type custom amounts (onboarding fees, monthly subscriptions, add-ons)
- Matter-linked billing is a future module, not this one

## 3. Architecture

```
modules/invoice/
├── controller/
│   └── InvoiceController.java          # REST API
├── service/
│   ├── InvoiceService.java             # Interface
│   ├── InvoiceServiceImpl.java         # Business logic
│   └── InvoicePdfService.java          # Thymeleaf + OpenHTMLtoPDF
├── repository/
│   └── InvoiceRepository.java          # JPA repo
│   └── InvoiceItemRepository.java      # JPA repo
├── entity/
│   ├── Invoice.java                    # Header entity
│   └── InvoiceItem.java                # Line item entity
├── dto/
│   ├── request/
│   │   ├── CreateInvoiceRequest.java
│   │   ├── UpdateInvoiceRequest.java
│   │   └── InvoiceItemRequest.java
│   └── response/
│       ├── InvoiceResponse.java
│       ├── InvoiceDetailResponse.java
│       └── InvoiceListResponse.java
├── enums/
│   └── InvoiceStatus.java
└── mapper/
    └── InvoiceMapper.java
```

Plus:
```
src/main/resources/templates/
└── invoice-template.html               # Thymeleaf PDF template
```

## 4. Entities

### Invoice

| Column | Type | Constraints | Notes |
|---|---|---|---|
| id | UUID | PK | Auto-generated |
| firm_id | UUID | FK → firms, NOT NULL | Target firm |
| invoice_number | VARCHAR(30) | UNIQUE, NOT NULL | Auto: INV-YYYY-NNNN |
| status | VARCHAR(15) | NOT NULL, default 'DRAFT' | DRAFT, SENT, PAID, OVERDUE, CANCELED |
| issue_date | DATE | NOT NULL | When issued |
| due_date | DATE | NOT NULL | Payment deadline |
| subtotal | DECIMAL(12,2) | NOT NULL | Sum of item amounts |
| tax_rate | DECIMAL(5,2) | default 0 | Tax percentage (e.g. 13.00 for 13% VAT) |
| tax_amount | DECIMAL(12,2) | NOT NULL | Computed: subtotal × tax_rate / 100 |
| total | DECIMAL(12,2) | NOT NULL | subtotal + tax_amount |
| notes | TEXT | nullable | Internal notes / payment instructions |
| payment_terms | VARCHAR(100) | nullable | e.g. "Net 30 days" |
| created_by | UUID | FK → users | Super Admin who created it |
| created_at | TIMESTAMP | auto | Audit |
| updated_at | TIMESTAMP | auto | Audit |

**Unique constraint:** (firm_id, invoice_number) — per-firm numbering

### InvoiceItem

| Column | Type | Constraints | Notes |
|---|---|---|---|
| id | UUID | PK | Auto-generated |
| invoice_id | UUID | FK → invoices, NOT NULL, CASCADE | Parent invoice |
| description | VARCHAR(500) | NOT NULL | Service/fee description |
| quantity | DECIMAL(10,2) | NOT NULL, default 1 | Hours, units, etc. |
| unit_price | DECIMAL(12,2) | NOT NULL | Rate per unit |
| amount | DECIMAL(12,2) | NOT NULL | quantity × unit_price |
| sort_order | INT | NOT NULL, default 0 | Display ordering |
| created_at | TIMESTAMP | auto | Audit |

## 5. Invoice Number Generation

Format: `INV-{YEAR}-{SEQ}`

- YEAR = 4-digit year (e.g. 2025)
- SEQ = 4-digit zero-padded sequential number per year
- Example: `INV-2025-0001`, `INV-2025-0002`, ..., `INV-2026-0001`

Implementation: Query `MAX(invoice_number)` for the current year, parse the sequence, increment.

## 6. API Endpoints

All endpoints are under `/api/v1/super-admin/invoices`.
Protected by `@PreAuthorize("hasRole('SUPER_ADMIN')")`.

### 6.1 List Invoices
```
GET /api/v1/super-admin/invoices
```
**Query params:** `page`, `size`, `status` (optional), `firmId` (optional), `search` (optional — invoice number or firm name)
**Response:** `PagedResponse<InvoiceListResponse>`

### 6.2 Get Invoice Detail
```
GET /api/v1/super-admin/invoices/{id}
```
**Response:** `InvoiceDetailResponse` (includes line items)

### 6.3 Create Invoice
```
POST /api/v1/super-admin/invoices
```
**Request body:**
```json
{
  "firmId": "uuid",
  "issueDate": "2025-08-25",
  "dueDate": "2025-09-24",
  "taxRate": 13.00,
  "paymentTerms": "Net 30 days",
  "notes": "Onboarding fee for SECFIRMA",
  "items": [
    {
      "description": "Platform Setup Fee",
      "quantity": 1,
      "unitPrice": 15000.00
    },
    {
      "description": "Monthly Subscription (Aug 2025)",
      "quantity": 1,
      "unitPrice": 5000.00
    }
  ]
}
```
**Response:** `InvoiceResponse` (header only, items computed server-side)
**Side effects:** Subtotal, taxAmount, total computed automatically. Status starts as DRAFT.

### 6.4 Update Invoice
```
PUT /api/v1/super-admin/invoices/{id}
```
**Constraint:** Only DRAFT invoices can be updated.
**Request body:** Same as create (items replaced entirely).

### 6.5 Delete Invoice
```
DELETE /api/v1/super-admin/invoices/{id}
```
**Constraint:** Only DRAFT invoices can be deleted.

### 6.6 Change Status
```
PATCH /api/v1/super-admin/invoices/{id}/status
```
**Request body:** `{ "status": "SENT" }`
**Allowed transitions:**
- DRAFT → SENT, CANCELED
- SENT → PAID, OVERDUE, CANCELED
- OVERDUE → PAID, CANCELED
- PAID → (terminal)
- CANCELED → (terminal)

### 6.7 Download PDF
```
GET /api/v1/super-admin/invoices/{id}/pdf
```
**Response:** `application/pdf` — binary stream with `Content-Disposition: attachment`

### 6.8 Send Invoice via Email
```
POST /api/v1/super-admin/invoices/{id}/send
```
**Side effects:**
1. Generates PDF
2. Looks up firm admin email from the firm's FIRM_ADMIN user
3. Sends email with PDF as attachment via `EmailService`
4. Updates status to SENT

## 7. PDF Template (Thymeleaf)

### Design
- A4 portrait layout
- Header: Platform name/logo + "INVOICE" title
- Invoice meta: number, date, due date, status badge
- Bill-to: Firm name, address, contact (from firm entity)
- Line items table: #, Description, Qty, Rate, Amount
- Summary: Subtotal, Tax (rate%), Total
- Payment info section: Bank details, notes
- Footer: Terms and conditions

### Styling
- Professional colors: dark navy header (#1A237E), clean white body
- Font: DejaVu Sans (bundled with OpenHTMLtoPDF — supports Unicode/Nepali chars)
- Table borders, alternating row colors
- Status badge colored by state (green=PAID, yellow=SENT, red=OVERDUE)

### Data Context
Thymeleaf context receives:
- `invoice` — the Invoice entity
- `items` — list of InvoiceItem entities
- `firm` — the Firm entity (name, address, logo)
- `platformName` — from SystemConfig
- `platformAddress` — from SystemConfig
- `generatedAt` — timestamp of PDF generation

## 8. Email Integration

New method on `EmailService`:
```java
void sendInvoiceEmail(UUID firmId, UUID recipientUserId, String toEmail,
                       String firmName, String invoiceNumber,
                       BigDecimal total, byte[] pdfBytes);
```

Implementation uses `JavaMailSender` with `MimeMessageHelper` to attach the PDF.

## 9. RBAC & Permissions

| Action | SUPER_ADMIN | FIRM_ADMIN |
|---|---|---|
| Create invoice | ✅ | ❌ |
| List all invoices | ✅ | ❌ (own firm only — future) |
| View invoice detail | ✅ | ✅ (own firm — future) |
| Update/delete invoice | ✅ (DRAFT only) | ❌ |
| Download PDF | ✅ | ✅ (own firm — future) |
| Send invoice | ✅ | ❌ |

For this iteration: all endpoints require `SUPER_ADMIN` role. Firm-scoped access is a follow-up.

## 10. Testing

Unit tests:
- `InvoiceServiceTest` — create, update, delete, status transitions, number generation
- `InvoicePdfServiceTest` — PDF generation returns non-empty bytes
- `InvoiceMapperTest` — entity ↔ DTO mapping

Integration tests:
- Full flow: create invoice → list → get detail → generate PDF → send email
- Status transition validation (invalid transitions throw)
- DRAFT-only guard on update/delete

## 11. Dependencies to Add

```xml
<dependency>
    <groupId>com.openhtmltopdf</groupId>
    <artifactId>openhtmltopdf-pdfbox</artifactId>
    <version>1.0.10</version>
</dependency>
<dependency>
    <groupId>com.openhtmltopdf</groupId>
    <artifactId>openhtmltopdf-svg-support</artifactId>
    <version>1.0.10</version>
</dependency>
```

## 12. Follow-ups (Not in This Iteration)

- Firm-scoped invoice listing (`/api/v1/firm/invoices`)
- Invoice PDF storage on disk / S3
- Payment tracking (mark as paid, record payment date/method)
- Recurring invoice generation (scheduled)
- Multi-currency support
- Invoice templates (multiple designs)
