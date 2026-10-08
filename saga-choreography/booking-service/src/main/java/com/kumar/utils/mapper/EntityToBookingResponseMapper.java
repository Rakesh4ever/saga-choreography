package com.kumar.utils.mapper;

import com.kumar.entity.Booking;
import com.kumar.response.BookingResponse;

public class EntityToBookingResponseMapper {

    public static BookingResponse map(Booking booking) {
        return new BookingResponse(booking.getBookingCode(),
                booking.getStatus());
    }
}
