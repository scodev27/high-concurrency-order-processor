package com.example.orderprocessor;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * Entry point.
 *
 * @EnableScheduling turns on the @Scheduled poller that will drive the
 * whole pipeline once we add it (OrderPollingScheduler).
 */
@SpringBootApplication
@EnableScheduling
public class OrderProcessorApplication {

    public static void main(String[] args) {
        SpringApplication.run(OrderProcessorApplication.class, args);
    }
}