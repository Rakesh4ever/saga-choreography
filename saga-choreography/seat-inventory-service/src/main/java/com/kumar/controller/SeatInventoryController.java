package com.kumar.controller;

import com.kumar.entity.SeatInventory;
import com.kumar.events.BookingCreatedEvent;
import com.kumar.service.SeatInventoryService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/seat-inventory-service")
public class SeatInventoryController {

    private final SeatInventoryService seatInventoryService;

    public SeatInventoryController(SeatInventoryService seatInventoryService) {
        this.seatInventoryService = seatInventoryService;
    }

    @GetMapping("/seats")
    public ResponseEntity<List<SeatInventory>> getAllSeats() {
        return ResponseEntity.ok(seatInventoryService.getAllSeats());
    }

    @GetMapping("/seats/show/{showId}")
    public ResponseEntity<List<SeatInventory>> getSeatsByShow(@PathVariable String showId) {
        return ResponseEntity.ok(seatInventoryService.getSeatsByShowId(showId));
    }

    @GetMapping("/seats/booking/{bookingId}")
    public ResponseEntity<List<SeatInventory>> getSeatsByBooking(@PathVariable String bookingId) {
        return ResponseEntity.ok(seatInventoryService.getSeatsByBookingId(bookingId));
    }

    @PostMapping("/seats/reserve")
    public ResponseEntity<Map<String, Object>> reserveSeats(@RequestBody BookingCreatedEvent event) {
        seatInventoryService.handleBooking(event);
        List<SeatInventory> lockedSeats = seatInventoryService.getSeatsByBookingId(event.bookingId());
        return ResponseEntity.ok(Map.of(
                "bookingId", event.bookingId(),
                "reserved", !lockedSeats.isEmpty(),
                "seats", lockedSeats
        ));
    }

    @PostMapping("/seats/release/{bookingId}")
    public ResponseEntity<Map<String, Object>> releaseSeats(@PathVariable String bookingId) {
        seatInventoryService.releaseSeatsOnPaymentFailure(bookingId);
        return ResponseEntity.ok(Map.of(
                "bookingId", bookingId,
                "status", "RELEASED"
        ));
    }
}
