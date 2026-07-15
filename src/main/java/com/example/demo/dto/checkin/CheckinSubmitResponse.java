package com.example.demo.dto.checkin;

import java.time.LocalDateTime;

public record CheckinSubmitResponse(
        Long conversationId,
        LocalDateTime savedAt
) {
}
