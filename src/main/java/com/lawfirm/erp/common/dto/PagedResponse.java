package com.lawfirm.erp.common.dto;

import lombok.Builder;
import lombok.Data;
import org.springframework.data.domain.Page;

import java.util.List;

/**
 * Generic paginated response — wraps any Page<T> into a consistent shape.
 *
 * Usage in any service:
 *   Page<User> page = userRepository.findByFirmId(firmId, PageRequest.of(pageNo, size, Sort.by("createdAt").descending()));
 *   return PagedResponse.of(page, page.getContent().stream().map(this::toDto).toList());
 *
 * Usage in any controller:
 *   @GetMapping
 *   public ResponseEntity<ApiResponse<PagedResponse<EmployeeResponse>>> list(
 *       @RequestParam(defaultValue = "0") int page,
 *       @RequestParam(defaultValue = "20") int size) {
 *       return responseHandler.ok(employeeService.getAll(page, size), "Fetched");
 *   }
 */
@Data
@Builder
public class PagedResponse<T> {

    private List<T> content;       // the actual items
    private int page;              // current page (0-based)
    private int size;              // items per page
    private long totalElements;    // total items across all pages
    private int totalPages;        // total number of pages
    private boolean first;         // is this the first page?
    private boolean last;          // is this the last page?
    private boolean empty;         // is the content empty?

    /**
     * Build a PagedResponse from a Spring Data Page + already-mapped DTO list.
     * Use when you need to map entities → DTOs before returning.
     *
     *   Page<User> page = repo.findAll(pageable);
     *   return PagedResponse.of(page, page.getContent().stream().map(toDto).toList());
     */
    public static <T> PagedResponse<T> of(Page<?> page, List<T> mappedContent) {
        return PagedResponse.<T>builder()
                .content(mappedContent)
                .page(page.getNumber())
                .size(page.getSize())
                .totalElements(page.getTotalElements())
                .totalPages(page.getTotalPages())
                .first(page.isFirst())
                .last(page.isLast())
                .empty(page.isEmpty())
                .build();
    }

    /**
     * Build a PagedResponse directly from a typed Spring Data Page<T>.
     * Use when the Page already contains DTOs (no mapping needed).
     *
     *   Page<EmployeeResponse> page = repo.findDtos(pageable);
     *   return PagedResponse.of(page);
     */
    public static <T> PagedResponse<T> of(Page<T> page) {
        return of(page, page.getContent());
    }
}