package com.kumar.service;

import com.kumar.entity.SeatInventory;
import com.kumar.events.BookingCreatedEvent;
import com.kumar.events.SeatReservedEvent;
import com.kumar.messaging.SeatReserveProducer;
import com.kumar.repository.SeatInventoryRepository;
import com.kumar.utils.enums.SeatStatus;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class SeatInventoryService {

    private static final Logger log = LoggerFactory.getLogger(SeatInventoryService.class);

    private final SeatInventoryRepository seatInventoryRepository;
    private final SeatReserveProducer seatReserveProducer;

    public SeatInventoryService(SeatInventoryRepository seatInventoryRepository,
                                SeatReserveProducer seatReserveProducer) {
        this.seatReserveProducer = seatReserveProducer;
        this.seatInventoryRepository = seatInventoryRepository;
    }


    // Add service methods to manage seat inventory

    public void handleBooking(BookingCreatedEvent event) {

        log.info("SeatInventoryService:: Processing bookingCreated for bookingId {}", event.bookingId());

        // Fetch seat inventories for the given show and seat numbers
        List<SeatInventory> seats = seatInventoryRepository
                .findByShowIdAndSeatNumberIn(event.showId(), event.seatIds());

        // Check if all requested seats exist and are available
        boolean allAvailable = event.seatIds() != null
                && !event.seatIds().isEmpty()
                && seats.size() == event.seatIds().size()
                && seats.stream().allMatch(s -> s.getStatus() == SeatStatus.AVAILABLE);

        if (allAvailable) {
            // Update seat status to LOCKED and set current booking ID
            seats.forEach(s -> {
                s.setStatus(SeatStatus.LOCKED);
                s.setCurrentBookingId(event.bookingId());
            });
            seatInventoryRepository.saveAll(seats);
            // Publish seat reserved event
            seatReserveProducer
                    .publishSeatReserveEvents(new SeatReservedEvent(event.bookingId(), true, event.amount()));
            log.info("SeatInventoryService:: Seats locked successfully for bookingId {}", event.bookingId());
        }else{
            log.warn("SeatInventoryService:: Seat locking failed for bookingId {}. Some seats are not available.", event.bookingId());
            // Publish seat reserved event with failure
            seatReserveProducer
                    .publishSeatReserveEvents(new SeatReservedEvent(event.bookingId(), false, event.amount()));
        }
    }

    public void releaseSeatsOnPaymentFailure(String bookingId) {
        log.info("SeatInventoryService:: Releasing seats for bookingId {}", bookingId);

        List<SeatInventory> bookingSeats = seatInventoryRepository.findByCurrentBookingId(bookingId);

        bookingSeats.forEach(s -> {
            s.setStatus(SeatStatus.AVAILABLE);
            s.setCurrentBookingId(null);
        });

        seatInventoryRepository.saveAll(bookingSeats);
        log.info("SeatInventoryService:: Seats released successfully for bookingId {}", bookingId);

        //send failed event to downstream (booking-service)

        seatReserveProducer
                .publishSeatReserveEvents(new SeatReservedEvent(bookingId, false, 0));
    }

    public List<SeatInventory> getAllSeats() {
        return seatInventoryRepository.findAll();
    }

    public List<SeatInventory> getSeatsByShowId(String showId) {
        return seatInventoryRepository.findAll().stream()
                .filter(s -> showId.equals(s.getShowId()))
                .toList();
    }

    public List<SeatInventory> getSeatsByBookingId(String bookingId) {
        return seatInventoryRepository.findByCurrentBookingId(bookingId);
    }
}
