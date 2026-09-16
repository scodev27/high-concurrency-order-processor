package com.example.orderprocessor.repository;

import com.example.orderprocessor.entity.Order;
import com.example.orderprocessor.entity.OrderStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.UUID;

public interface OrderRepository extends JpaRepository<Order, UUID> {

    /**
     * ARCHITECTURAL NOTE - FOR UPDATE SKIP LOCKED
     * ---------------------------------------------------------------------
     * A plain "SELECT ... WHERE status = PENDING" lets two concurrent
     * workers grab the same rows before either commits. Adding FOR UPDATE
     * takes row-level locks within the current transaction, so a second
     * worker's FOR UPDATE would normally BLOCK waiting for the first one
     * to commit. SKIP LOCKED changes that: instead of blocking, PostgreSQL
     * silently skips any row already locked by another transaction and
     * returns the next available ones.
     *
     * Result: N workers can poll in parallel, each atomically claiming a
     * disjoint batch of rows, with zero contention and no worker ever
     * waiting on another.
     *
     * ORDER BY created_at keeps processing roughly FIFO; without it,
     * SKIP LOCKED is free to return rows in an arbitrary order.
     */
    @Query(value = """
            SELECT * FROM orders
            WHERE status = 'PENDING'
            ORDER BY created_at
            FOR UPDATE SKIP LOCKED
            LIMIT :limit
            """, nativeQuery = true)
    List<Order> lockAndFetchPendingBatch(@Param("limit") int limit);

    /**
     * Bulk status update used to close out a batch. A bulk
     * UPDATE ... WHERE id IN (...) is preferred over loading each entity
     * and mutating it: by the time this runs, the entities fetched in the
     * claiming transaction are already detached, so re-attaching them
     * would mean extra round-trips for no benefit.
     */
    @Modifying(clearAutomatically = true)
    @Query("UPDATE Order o SET o.status = :status WHERE o.id IN :ids")
    int updateStatusForIds(@Param("ids") List<UUID> ids, @Param("status") OrderStatus status);

    long countByStatus(OrderStatus status);
}