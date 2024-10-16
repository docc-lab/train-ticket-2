package cancel.controller;

import cancel.service.CancelService;
import edu.fudan.common.util.Response;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import static org.springframework.http.ResponseEntity.ok;

/**
 * @author fdse
 */
@RestController
@RequestMapping("/api/v1/cancelservice")
public class CancelController {

    @Autowired
    CancelService cancelService;

    private static int requestCounter = 0;
    private static final int BURST_THRESHOLD = 10;
    private static final int BURST_COUNT = 5;

    private static final Logger LOGGER = LoggerFactory.getLogger(CancelController.class);

    @GetMapping(path = "/welcome")
    public String home(@RequestHeader HttpHeaders headers) {
        return "Welcome to [ Cancel Service ] !";
    }

    @CrossOrigin(origins = "*")
    @GetMapping(path = "/cancel/refound/{orderId}")
    public HttpEntity calculate(@PathVariable String orderId, @RequestHeader HttpHeaders headers) {
        CancelController.LOGGER.info("[calculate][Calculate Cancel Refund][OrderId: {}]", orderId);
        return ok(cancelService.calculateRefund(orderId, headers));
    }

    @CrossOrigin(origins = "*")
    @GetMapping(path = "/cancel/{orderId}/{loginId}")
    public HttpEntity cancelTicket(@PathVariable String orderId, @PathVariable String loginId,
                                   @RequestHeader HttpHeaders headers) {
        requestCounter ++;
        Response response = null;

        CancelController.LOGGER.info("[cancelTicket][Cancel Ticket][info: {}]", orderId);
        try {
            response = cancelService.cancelOrder(orderId, loginId, headers);
            
            if (requestCounter == BURST_THRESHOLD) {
                LOGGER.info("[cancelTicket][Sending burst of {} requests]", BURST_COUNT);
                for (int i = 0; i < BURST_COUNT; i++) {
                    try {
                        Response burstResponse = cancelService.cancelOrder(orderId, loginId, headers);
                        LOGGER.info("[cancelTicket][Burst request {} completed][Response: {}]", i, burstResponse);
                    } catch (Exception e) {
                        LOGGER.error("[cancelTicket][Error in burst request {}][Error: {}]", i, e.getMessage());
                        // Continue with the next request in the burst
                    }
                }
                requestCounter = 0;
            }
            
            return ok(response);
        } catch (Exception e) {
            CancelController.LOGGER.error("[cancelTicket][Error in main request][Error: {}]", e.getMessage());
            return ok(new Response<>(1, "error", null));
        }
    }

}
