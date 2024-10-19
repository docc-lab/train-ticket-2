package cancel.controller;

import cancel.service.CancelService;
import edu.fudan.common.util.Response;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.web.bind.annotation.*;

import javax.annotation.PreDestroy;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.springframework.http.ResponseEntity.ok;

/**
 * @author fdse
 */
@RestController
@RequestMapping("/api/v1/cancelservice")
public class CancelController {

    @Autowired
    CancelService cancelService;

    private static final AtomicInteger requestCounter = new AtomicInteger(0);
    private static final int BURST_THRESHOLD = 10; // Trigger burst every 10 cancel requests
    private static final int BURST_COUNT = 5; // Generate 5 extra cancel requests

    private static final Logger LOGGER = LoggerFactory.getLogger(CancelController.class);
    private static final ExecutorService executorService = Executors.newFixedThreadPool(BURST_COUNT);

    @GetMapping(path = "/welcome")
    public String home(@RequestHeader HttpHeaders headers) {
        return "Welcome to [ Cancel Service ] !";
    }

    @CrossOrigin(origins = "*")
    @GetMapping(path = "/cancel/refound/{orderId}")
    public HttpEntity calculate(@PathVariable String orderId, @RequestHeader HttpHeaders headers) {
        LOGGER.info("[calculate][Calculate Cancel Refund][OrderId: {}]", orderId);
        return ok(cancelService.calculateRefund(orderId, headers));
    }

    @CrossOrigin(origins = "*")
    @GetMapping(path = "/cancel/{orderId}/{loginId}")
    public HttpEntity cancelTicket(@PathVariable String orderId, @PathVariable String loginId,
                                   @RequestHeader HttpHeaders headers) {
        int currentCount = requestCounter.incrementAndGet();
        LOGGER.info("[cancelTicket][Cancel Ticket][Start][OrderId: {}, RequestCount: {}]", orderId, currentCount);

        try {
            Response response = cancelService.cancelOrder(orderId, loginId, headers);
            
            if (currentCount % BURST_THRESHOLD == 0) {
                sendBurstRequests(orderId, loginId, headers);
            }

            LOGGER.info("[cancelTicket][Cancel Ticket][End][OrderId: {}, RequestCount: {}]", orderId, currentCount);
            return ok(response);
        } catch (Exception e) {
            LOGGER.error("[cancelTicket][Error in main request][Error: {}]", e.getMessage());
            return ok(new Response<>(1, "error", null));
        }
    }

    private void sendBurstRequests(String orderId, String loginId, HttpHeaders headers) {
        LOGGER.info("[sendBurstRequests][Sending burst of {} requests concurrently]", BURST_COUNT);
        
        for (int i = 0; i < BURST_COUNT; i++) {
            final int index = i;
            executorService.submit(() -> {
                try {
                    Response burstResponse = cancelService.cancelOrder(orderId, loginId, headers);
                    LOGGER.info("[sendBurstRequests][Burst request {} completed][Response: {}]", index, burstResponse);
                } catch (Exception e) {
                    LOGGER.error("[sendBurstRequests][Error in burst request {}][Error: {}]", index, e.getMessage());
                }
            });
        }

        LOGGER.info("[sendBurstRequests][Burst requests submitted.]");
    }

    @PreDestroy
    public void shutdownExecutorService() {
        executorService.shutdown();
        try {
            if (!executorService.awaitTermination(30, TimeUnit.SECONDS)) {
                executorService.shutdownNow();
            }
        } catch (InterruptedException e) {
            executorService.shutdownNow();
        }
    }
}