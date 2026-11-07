package com.kumar.controller;

import com.kumar.events.SeatReservedEvent;
import com.kumar.service.PaymentService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/payment-service")
public class PaymentController {

    private final PaymentService paymentService;

    public PaymentController(PaymentService paymentService) {
        this.paymentService = paymentService;
    }

    @GetMapping("/health")
    public ResponseEntity<Map<String, Object>> health() {
        return ResponseEntity.ok(Map.of(
                "service", "payment-service",
                "status", "UP",
                "simulatedPaymentLimit", 2000
        ));
    }

    @PostMapping("/process")
    public ResponseEntity<Map<String, Object>> processPayment(@RequestBody SeatReservedEvent event) {
        paymentService.processPayment(event);
        boolean paymentCompleted = event.amount() <= 2000;
        return ResponseEntity.ok(Map.of(
                "bookingId", event.bookingId(),
                "amount", event.amount(),
                "paymentCompleted", paymentCompleted,
                "status", paymentCompleted ? "SUCCESS" : "FAILED"
        ));
    }
}
