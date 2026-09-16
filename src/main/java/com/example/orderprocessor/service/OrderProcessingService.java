package com.example.orderprocessor.service;

import com.example.orderprocessor.entity.Order;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * ARCHITECTURAL NOTE - THE ORCHESTRATOR HAS NO @Transactional ON IT
 * -----------------------------------------------------------------------
 * This is intentional. If processBatch() carried @Transactional, Spring
 * would open one connection at the start and hold it open for the entire
 * method - including the Thread.sleep() below that stands in for a slow
 * external call. Under load, every concurrent worker would hold a
 * connection hostage for the full duration of the "heavy work", and the
 * Hikari pool would exhaust almost immediately.
 *
 * Instead, processBatch() delegates to OrderTransactionService for two
 * short, separate transactions, with the slow part running in between
 * while holding NO database connection at all:
 *
 *   [Short TX 1: claim]  ->  [no TX: heavy work]  ->  [Short TX 2: complete]
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class OrderProcessingService {

    private final OrderTransactionService orderTransactionService;

    @Value("${order-processor.batch-size:10}")
    private int batchSize;

    @Value("${order-processor.simulated-work-ms:2000}")
    private long simulatedWorkMs;

    public void processBatch() {
        List<Order> claimed = orderTransactionService.claimBatch(batchSize);
        if (claimed.isEmpty()) {
            return;
        }

        // OUTSIDE ANY TRANSACTION: no DB connection is held while this runs.
        // In a real system this is where you'd call a slow external API,
        // render a file, run a report, etc.
        simulateExternalWork();

        List<UUID> ids = claimed.stream()
                .map(Order::getId)
                .collect(Collectors.toList());

        orderTransactionService.completeBatch(ids);
    }

    private void simulateExternalWork() {
        try {
            Thread.sleep(simulatedWorkMs);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            log.warn("Simulated work was interrupted", e);
        }
    }
}