package com.kumar.service;

import com.kumar.entity.Booking;
import com.kumar.events.BookingCreatedEvent;
import com.kumar.messaging.BookingEventProducer;
import com.kumar.repository.BookingRepository;
import com.kumar.request.BookingRequest;
import com.kumar.response.BookingResponse;
import com.kumar.utils.mapper.EntityToBookingResponseMapper;
import com.kumar.utils.mapper.BookingRequestToEntityMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;


@Service
@Slf4j
public class BookingService {

    private final BookingRepository bookingRepository;

    private final BookingEventProducer bookingEventProducer;

    public BookingService(BookingRepository bookingRepository, BookingEventProducer bookingEventProducer) {
        this.bookingEventProducer = bookingEventProducer;
        this.bookingRepository = bookingRepository;
    }

    /**
     * Reserves seats for a given show and user.
     * Validates the request, maps it to an entity, persists it and returns a response DTO.
     *
     * @param request the seat reservation request
     * @return SeatReserveResponse containing reservation details
     * @throws IllegalArgumentException if the request is invalid
     */

    public BookingResponse bookSeats(BookingRequest request) {

        log.info("Booking seats for user {} for show {}", request.userId(), request.showId());

        // Map request -> entity
        var reservationEntity = BookingRequestToEntityMapper.map(request);

        // Persist and map to response
        var savedReservation = bookingRepository.save(reservationEntity);

        // Publish booking created event
        var bookingCreatedEvent = buildBookingCreateEvents(savedReservation);
        bookingEventProducer.publishBookingEvents(bookingCreatedEvent);

        var response = EntityToBookingResponseMapper.map(savedReservation);

        log.info("Seats confirmed with reservation id {}", response.reservationId());
        return response;
    }

    private BookingCreatedEvent buildBookingCreateEvents(Booking savedReservation) {
        return new BookingCreatedEvent(savedReservation.getBookingCode(), savedReservation.getUserId(), savedReservation.getShowId(), savedReservation.getSeatIds(), savedReservation.getAmount());
    }


    public void handleBookingOnSeatReservationFailure(String bookingId) {
        log.info("BookingService:: Handling booking failure for bookingId {}", bookingId);
        var bookingDetails=bookingRepository.findByBookingCode(bookingId);
        if(bookingDetails!=null){
            bookingDetails.setStatus("FAILED");
            bookingRepository.save(bookingDetails);
            log.info("BookingService:: Booking marked as FAILED for bookingId {}", bookingId);
        }else{
            log.warn("BookingService:: No booking found with bookingId {}", bookingId);
        }

    }
}
