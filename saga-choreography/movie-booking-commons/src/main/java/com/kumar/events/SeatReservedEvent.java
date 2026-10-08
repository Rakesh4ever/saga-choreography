package com.kumar.events;

public record SeatReservedEvent(String bookingId, boolean reserved, long amount) {}