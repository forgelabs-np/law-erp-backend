package com.lawfirm.erp.modules.invoice.repository;

import com.lawfirm.erp.modules.invoice.entity.Invoice;
import com.lawfirm.erp.modules.invoice.enums.InvoiceStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface InvoiceRepository extends JpaRepository<Invoice, UUID> {

    Page<Invoice> findByFirmId(UUID firmId, Pageable pageable);

    Page<Invoice> findByStatus(InvoiceStatus status, Pageable pageable);

    Page<Invoice> findByFirmIdAndStatus(UUID firmId, InvoiceStatus status, Pageable pageable);

    Optional<Invoice> findByIdAndFirmId(UUID id, UUID firmId);

    /** Generate next invoice number for a given year. */
    @Query("SELECT i.invoiceNumber FROM Invoice i WHERE i.invoiceNumber LIKE :pattern ORDER BY i.invoiceNumber DESC")
    List<String> findMaxInvoiceNumberByPattern(@Param("pattern") String pattern, Pageable pageable);

    long countByFirmId(UUID firmId);

    long countByStatus(InvoiceStatus status);

    /** Search by invoice number or firm name (firm name requires a join). */
    @Query("SELECT i FROM Invoice i LEFT JOIN com.lawfirm.erp.firm.entity.Firm f ON f.id = i.firmId " +
           "WHERE (:search IS NULL OR LOWER(i.invoiceNumber) LIKE LOWER(CONCAT('%', CAST(:search AS string), '%')) " +
           "     OR LOWER(f.name) LIKE LOWER(CONCAT('%', CAST(:search AS string), '%'))) " +
           "AND (:status IS NULL OR i.status = :status) " +
           "AND (:firmId IS NULL OR i.firmId = :firmId) " +
           "ORDER BY i.createdAt DESC")
    Page<Invoice> search(@Param("search") String search,
                          @Param("status") InvoiceStatus status,
                          @Param("firmId") UUID firmId,
                          Pageable pageable);
}
