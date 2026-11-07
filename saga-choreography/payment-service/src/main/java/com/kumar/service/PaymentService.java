package com.kumar.service;

import com.kumar.PaymentEventsProducer;
import com.kumar.events.SeatReservedEvent;
import com.kumar.exception.PaymentServiceException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;


@Service
public class PaymentService {

    private static final Logger log = LoggerFactory.getLogger(PaymentService.class);

    private final PaymentEventsProducer eventsProducer;

    public PaymentService(PaymentEventsProducer eventsProducer) {
        this.eventsProducer = eventsProducer;
    }


    public void processPayment(SeatReservedEvent event) {

        try {
            log.info("Processing payment for bookingId: {}", event.bookingId());

            // Simulate payment failure scenario
            if (event.amount() > 2000) {
                log.info("Payment amount exceeds limit for bookingId: {}", event.bookingId());
                // failure events
                eventsProducer.publishPaymentFailureEvent(event);
                //throw new RuntimeException("Payment amount exceeds limit");
            }else{
                // success event
                eventsProducer.publishPaymentSuccessEvent(event);
                log.info("✅ Payment successful for bookingId: {}", event.bookingId());

            }


        }catch (Exception e){
            log.error("❌ Payment failed for bookingId: {}. Reason: {}", event.bookingId(), e.getMessage());
            throw new PaymentServiceException("Payment processing failed for bookingId: " + event.bookingId());
        }
    }

}
