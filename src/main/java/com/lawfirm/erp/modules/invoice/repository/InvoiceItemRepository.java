package com.lawfirm.erp.modules.invoice.repository;

import com.lawfirm.erp.modules.invoice.entity.InvoiceItem;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface InvoiceItemRepository extends JpaRepository<InvoiceItem, UUID> {

    List<InvoiceItem> findByInvoiceIdOrderBySortOrderAsc(UUID invoiceId);

    @Modifying
    @Query("DELETE FROM InvoiceItem ii WHERE ii.invoice.id = :invoiceId")
    void deleteByInvoiceId(@Param("invoiceId") UUID invoiceId);
}
