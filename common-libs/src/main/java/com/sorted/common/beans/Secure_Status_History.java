package com.sorted.common.beans;

import com.sorted.common.enums.SecureReturnStatus;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serial;
import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * Tracks status history for secure return process.
 * Maintains audit trail of all status changes.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Secure_Status_History implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    private SecureReturnStatus status;
    private LocalDateTime changed_at;
    private String changed_by;
    private String remarks;
}
