package com.enterprise.transaction.dto;

import lombok.*;
import java.util.Map;

@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class ProcessingSummary {
    private long totalProcessed;
    private long totalDuplicates;
    private long totalPendingSequence;
    private long totalFailed;
    private Map<String, Long> statusCounts;
}
