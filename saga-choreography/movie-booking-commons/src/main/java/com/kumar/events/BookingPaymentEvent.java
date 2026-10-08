package com.kumar.events;

public record BookingPaymentEvent(String bookingId, boolean paymentCompleted, long amount) {
}
