package com.example.orderprocessor.entity;

/**
 * Lifecycle of an {@link Order}.
 *
 * PENDING    -> waiting to be picked up by a worker.
 * PROCESSING -> claimed by a worker; the "heavy work" is in flight.
 * COMPLETED  -> heavy work finished successfully.
 * FAILED     -> reserved for future use (e.g. after N retries or an
 *               unrecoverable error in the heavy-work step).
 */
public enum OrderStatus {
    PENDING,
    PROCESSING,
    COMPLETED,
    FAILED
}