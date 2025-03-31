package uk.gov.dwp.uc.pairtest;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import thirdparty.paymentgateway.TicketPaymentService;
import thirdparty.seatbooking.SeatReservationService;
import uk.gov.dwp.uc.pairtest.domain.TicketTypeRequest;
import uk.gov.dwp.uc.pairtest.exception.InvalidPurchaseException;

import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;
import static uk.gov.dwp.uc.pairtest.domain.TicketTypeRequest.Type.*;

@ExtendWith(MockitoExtension.class)
class CinemaTicketServiceTest {

    @Mock
    private TicketPaymentService paymentService;

    @Mock
    private SeatReservationService reservationService;

    @InjectMocks
    private TicketServiceImpl ticketService;

    @ParameterizedTest
    @MethodSource("validPurchase")
    void shouldProcessValidPurchases(Long accountId, TicketTypeRequest[] requests,
                                     int expectedPayment, int expectedSeats) {
        ticketService.purchaseTickets(accountId, requests);

        verify(paymentService).makePayment(accountId, expectedPayment);
        verify(reservationService).reserveSeat(accountId, expectedSeats);
    }

    private static Stream<Arguments> validPurchase() {
        return Stream.of(
                // Single adult ticket
                Arguments.of(1L,
                        new TicketTypeRequest[]{new TicketTypeRequest(ADULT, 1)},
                        25, 1),

                // Maximum tickets (20 adults + 5 children)
                Arguments.of(2L,
                        new TicketTypeRequest[]{
                                new TicketTypeRequest(ADULT, 20),
                                new TicketTypeRequest(CHILD, 5)
                        },
                        575, 25),

                // Mixed tickets with infants
                Arguments.of(3L,
                        new TicketTypeRequest[]{
                                new TicketTypeRequest(ADULT, 2),
                                new TicketTypeRequest(CHILD, 3),
                                new TicketTypeRequest(INFANT, 1)
                        },
                        95, 5),

                // Only adult tickets (no children/infants)
                Arguments.of(4L,
                        new TicketTypeRequest[]{new TicketTypeRequest(ADULT, 10)},
                        250, 10)
        );
    }

    @ParameterizedTest
    @MethodSource("invalidPurchase")
    void shouldRejectInvalidPurchases(Long accountId, TicketTypeRequest[] requests, String expectedMessage) {
        assertThatThrownBy(() -> ticketService.purchaseTickets(accountId, requests))
                .isInstanceOf(InvalidPurchaseException.class)
                .hasMessageContaining(expectedMessage);
    }

    private static Stream<Arguments> invalidPurchase() {
        return Stream.of(
                // No adult with child ticket
                Arguments.of(1L,
                        new TicketTypeRequest[]{new TicketTypeRequest(CHILD, 1)},
                        "Child and Infant tickets cannot be purchased without an Adult ticket"),

                // No adult with infant ticket
                Arguments.of(2L,
                        new TicketTypeRequest[]{new TicketTypeRequest(INFANT, 1)},
                        "Child and Infant tickets cannot be purchased without an Adult ticket"),

                // Too many tickets (26)
                Arguments.of(3L,
                        new TicketTypeRequest[]{new TicketTypeRequest(ADULT, 26)},
                        "Maximum of 25 tickets can be purchased at a time"),

                // Zero tickets requested
                Arguments.of(4L,
                        new TicketTypeRequest[]{new TicketTypeRequest(ADULT, 0)},
                        "No tickets requested"),

                // Negative ticket count
                Arguments.of(5L,
                        new TicketTypeRequest[]{new TicketTypeRequest(ADULT, -1)},
                        "No tickets requested"),

                // Null ticket requests
                Arguments.of(6L, null, "No ticket requests provided"),

                // Invalid account ID
                Arguments.of(0L,
                        new TicketTypeRequest[]{new TicketTypeRequest(ADULT, 1)},
                        "Invalid account ID")
        );
    }

    @Test
    void shouldCallServicesExactlyOnce() {
        ticketService.purchaseTickets(1L, new TicketTypeRequest(ADULT, 2));

        verify(paymentService, times(1)).makePayment(1L, 50);
        verify(reservationService, times(1)).reserveSeat(1L, 2);

        // Verify no more interactions using Mockito's verifyNoMoreInteractions
        verifyNoMoreInteractions(paymentService, reservationService);
    }

    @Test
    void shouldNotReserveSeatsForInfants() {
        ticketService.purchaseTickets(1L,
                new TicketTypeRequest(ADULT, 1),
                new TicketTypeRequest(INFANT, 3));

        verify(paymentService).makePayment(1L, 25);
        verify(reservationService).reserveSeat(1L, 1); // Only adult seat reserved
    }
}