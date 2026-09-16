package com.example.orderprocessor.service;

import com.example.orderprocessor.entity.Order;
import com.example.orderprocessor.entity.OrderStatus;
import com.example.orderprocessor.repository.OrderRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

/**
 * ARCHITECTURAL NOTE - WHY THIS IS A SEPARATE BEAN
 * -----------------------------------------------------------------------
 * Both methods here are deliberately short: open a transaction, touch the
 * database, get out. No sleeps, no HTTP calls, no "business logic" beyond
 * claiming/completing rows.
 *
 * They live in their own Spring bean rather than as methods on the
 * orchestrator service for a concrete reason: @Transactional works via a
 * dynamic proxy around the bean. If the orchestrator called
 * this.claimBatch(...) on itself, that call would bypass the proxy
 * entirely and @Transactional would silently do nothing. Splitting the
 * transactional methods into an injected collaborator bean means the call
 * always goes through the proxy, so the transaction boundaries are
 * actually honoured.
 *
 * Propagation.REQUIRES_NEW ensures each method always gets its own short,
 * dedicated transaction, even if a caller somehow already had one open.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class OrderTransactionService {

    private final OrderRepository orderRepository;

    /**
     * SHORT TRANSACTION 1
     * Claims a batch of PENDING orders (via SKIP LOCKED) and flips them to
     * PROCESSING, all inside one short transaction. The connection is
     * released as soon as this method returns.
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public List<Order> claimBatch(int limit) {
        List<Order> batch = orderRepository.lockAndFetchPendingBatch(limit);

        // Entities above are managed by THIS transaction's persistence
        // context. Mutating them is enough - Hibernate's dirty checking
        // flushes the resulting UPDATE automatically at commit time.
        batch.forEach(order -> order.setStatus(OrderStatus.PROCESSING));

        log.info("Claimed {} order(s) for processing", batch.size());
        return batch;
    }

    /**
     * SHORT TRANSACTION 2
     * Marks a previously claimed batch as COMPLETED. Called only after the
     * heavy work has already finished, outside of any transaction.
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void completeBatch(List<UUID> orderIds) {
        if (orderIds.isEmpty()) {
            return;
        }
        int updated = orderRepository.updateStatusForIds(orderIds, OrderStatus.COMPLETED);
        log.info("Marked {} order(s) as COMPLETED", updated);
    }
}