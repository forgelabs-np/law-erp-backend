package com.lawfirm.erp.modules.notification.controller;

import com.lawfirm.erp.common.dto.PagedResponse;
import com.lawfirm.erp.common.exception.ForbiddenException;
import com.lawfirm.erp.common.exception.GlobalExceptionHandler;
import com.lawfirm.erp.common.exception.ResponseHandler;
import com.lawfirm.erp.common.service.MessageSourceService;
import com.lawfirm.erp.modules.notification.dto.NotificationResponse;
import com.lawfirm.erp.modules.notification.service.NotificationService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Controller RED test — mirrors AuthorizationTestPattern.
 * Service is mocked and instantiated directly; ResponseHandler real.
 */
@ExtendWith(MockitoExtension.class)
class NotificationControllerTest {

    @Mock
    private NotificationService notificationService;

    private NotificationController controller;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        ResponseHandler responseHandler =
                new ResponseHandler(org.mockito.Mockito.mock(MessageSourceService.class));
        controller = new NotificationController(notificationService,
                org.mockito.Mockito.mock(com.lawfirm.erp.modules.notification.service.BroadcastService.class),
                org.mockito.Mockito.mock(com.lawfirm.erp.modules.notification.service.NotificationPreferenceService.class),
                responseHandler);
        mockMvc = MockMvcBuilders.standaloneSetup(controller)
                .setControllerAdvice(new GlobalExceptionHandler(responseHandler))
                .build();
    }

    private NotificationResponse response(boolean read) {
        return NotificationResponse.builder()
                .id(UUID.randomUUID())
                .type("CASE_ASSIGNED")
                .category("SYSTEM")
                .title("Assigned to matter")
                .body("You were assigned to matter M-1.")
                .referenceType("MATTER")
                .referenceId(UUID.randomUUID())
                .read(read)
                .createdAt(java.time.LocalDateTime.now())
                .build();
    }

    @Test
    @DisplayName("GET /notifications returns paged feed")
    @WithMockUser
    void listNotifications() throws Exception {
        NotificationResponse row = response(false);
        PagedResponse<NotificationResponse> paged = PagedResponse.<NotificationResponse>builder()
                .content(List.of(row)).page(0).size(20).totalElements(1)
                .totalPages(1).first(true).last(true).empty(false)
                .build();
        when(notificationService.getMyNotifications(eq(false), eq(0), eq(20))).thenReturn(paged);

        mockMvc.perform(get("/api/v1/notifications"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.content[0].type").value("CASE_ASSIGNED"))
                .andExpect(jsonPath("$.data.content[0].read").value(false));
    }

    @Test
    @DisplayName("GET /unread-count returns count payload for the badge")
    @WithMockUser
    void unreadCount() throws Exception {
        when(notificationService.getUnreadCount()).thenReturn(5L);

        mockMvc.perform(get("/api/v1/notifications/unread-count"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.count").value(5));
    }

    @Test
    @DisplayName("POST /{id}/read marks one notification read")
    @WithMockUser
    void markRead() throws Exception {
        mockMvc.perform(post("/api/v1/notifications/" + UUID.randomUUID() + "/read").with(csrf()))
                .andExpect(status().isOk());

        verify(notificationService).markAsRead(any());
    }

    @Test
    @DisplayName("POST /read-all returns updated count")
    @WithMockUser
    void markAllRead() throws Exception {
        when(notificationService.markAllAsRead()).thenReturn(3);

        mockMvc.perform(post("/api/v1/notifications/read-all").with(csrf()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.updated").value(3));
    }

    @Test
    @DisplayName("Marking someone else's notification read → 403 via exception advice")
    @WithMockUser
    void foreignNotification_forbidden() throws Exception {
        org.mockito.Mockito.doThrow(new ForbiddenException("You can only manage your own notifications"))
                .when(notificationService).markAsRead(any());

        mockMvc.perform(post("/api/v1/notifications/" + UUID.randomUUID() + "/read").with(csrf()))
                .andExpect(status().isForbidden());
    }
}
