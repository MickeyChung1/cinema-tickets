package uk.gov.dwp.uc.pairtest;

import thirdparty.paymentgateway.TicketPaymentService;
import thirdparty.seatbooking.SeatReservationService;
import uk.gov.dwp.uc.pairtest.domain.TicketTypeRequest;
import uk.gov.dwp.uc.pairtest.exception.InvalidPurchaseException;

import java.util.Arrays;

public class TicketServiceImpl implements TicketService {

    private final TicketPaymentService paymentService;
    private final SeatReservationService reservationService;

    public TicketServiceImpl(TicketPaymentService paymentService,
                             SeatReservationService reservationService) {
        this.paymentService = paymentService;
        this.reservationService = reservationService;
    }

    @Override
    public void purchaseTickets(Long accountId, TicketTypeRequest... ticketTypeRequests)
            throws InvalidPurchaseException {
        validateRequest(accountId, ticketTypeRequests);

        int totalAmount = calculateTotalAmount(ticketTypeRequests);
        int totalSeats = calculateTotalSeats(ticketTypeRequests);

        paymentService.makePayment(accountId, totalAmount);
        reservationService.reserveSeat(accountId, totalSeats);
    }

    private void validateRequest(Long accountId, TicketTypeRequest... ticketTypeRequests)
            throws InvalidPurchaseException {
        if (accountId == null || accountId <= 0) {
            throw new InvalidPurchaseException("Invalid account ID");
        }

        if (ticketTypeRequests == null || ticketTypeRequests.length == 0) {
            throw new InvalidPurchaseException("No ticket requests provided");
        }

        int totalTickets = Arrays.stream(ticketTypeRequests)
                .mapToInt(TicketTypeRequest::getNoOfTickets)
                .sum();

        if (totalTickets <= 0) {
            throw new InvalidPurchaseException("No tickets requested");
        }

        if (totalTickets > 25) {
            throw new InvalidPurchaseException("Maximum of 25 tickets can be purchased at a time");
        }

        boolean hasAdultTicket = Arrays.stream(ticketTypeRequests)
                .anyMatch(request -> request.getTicketType() == TicketTypeRequest.Type.ADULT);

        if (!hasAdultTicket) {
            boolean hasChildOrInfant = Arrays.stream(ticketTypeRequests)
                    .anyMatch(request -> request.getTicketType() == TicketTypeRequest.Type.CHILD ||
                            request.getTicketType() == TicketTypeRequest.Type.INFANT);

            if (hasChildOrInfant) {
                throw new InvalidPurchaseException("Child and Infant tickets cannot be purchased without an Adult ticket");
            }
        }
    }

    private int calculateTotalAmount(TicketTypeRequest... ticketTypeRequests) {
        return Arrays.stream(ticketTypeRequests)
                .mapToInt(request -> {
                    switch (request.getTicketType()) {
                        case ADULT:
                            return 25 * request.getNoOfTickets();
                        case CHILD:
                            return 15 * request.getNoOfTickets();
                        case INFANT:
                            return 0;
                        default:
                            throw new InvalidPurchaseException("Unknown ticket type");
                    }
                })
                .sum();
    }

    private int calculateTotalSeats(TicketTypeRequest... ticketTypeRequests) {
        return Arrays.stream(ticketTypeRequests)
                .filter(request -> request.getTicketType() != TicketTypeRequest.Type.INFANT)
                .mapToInt(TicketTypeRequest::getNoOfTickets)
                .sum();
    }
}
