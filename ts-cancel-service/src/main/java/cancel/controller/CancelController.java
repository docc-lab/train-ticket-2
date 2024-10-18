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

// import for sharing counter across java instances of this service
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * @author fdse
 */
@RestController
@RequestMapping("/api/v1/cancelservice")
public class CancelController {

    @Autowired
    CancelService cancelService;

    private static final ConcurrentLinkedQueue<Long> requestTimestamps = new ConcurrentLinkedQueue<>();
    private static final AtomicInteger burstCounter = new AtomicInteger(0);
    private static final int BURST_THRESHOLD = 10; // every 10 cancel request 
    private static final int BURST_COUNT = 5; // generate 5 extra cancel request
    private static final long TIME_WINDOW_MS = 10000; // count for # cancel request within 10 second window

    private static final Logger LOGGER = LoggerFactory.getLogger(CancelController.class);
    private static final ExecutorService executorService = Executors.newFixedThreadPool(BURST_COUNT);

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
        long currentTime = System.currentTimeMillis();
        requestTimestamps.add(currentTime);

        // Remove timestamps older than the time window
        while (!requestTimestamps.isEmpty() && requestTimestamps.peek() < currentTime - TIME_WINDOW_MS) {
            requestTimestamps.poll();
        }

        int requestCount = requestTimestamps.size();
        LOGGER.info("[cancelTicket][Cancel Ticket][Start][OrderId: {}, RequestCount: {}]", orderId, requestCount);

        boolean shouldBurst = false;
        if (requestCount >= BURST_THRESHOLD && burstCounter.get() == 0) {
            shouldBurst = burstCounter.compareAndSet(0, 1);
            if (shouldBurst) {
                LOGGER.info("[cancelTicket][Burst threshold reached. Will perform burst after this request.]");
            }
        }

        try {
            Response response = cancelService.cancelOrder(orderId, loginId, headers);
            // send bursty workload concurrently
            if (shouldBurst) {
                sendBurstRequests(orderId, loginId, headers);
            }

            LOGGER.info("[cancelTicket][Cancel Ticket][End][OrderId: {}, Final RequestCount: {}]", orderId, requestTimestamps.size());
            return ok(response);
        } catch (Exception e) {
            CancelController.LOGGER.error("[cancelTicket][Error in main request][Error: {}]", e.getMessage());
            return ok(new Response<>(1, "error", null));
        }
    }

    private void sendBurstRequests(String orderId, String loginId, HttpHeaders headers) {
        LOGGER.info("[sendBurstRequests][Sending burst of {} requests concurrently]", BURST_COUNT);
        
        CountDownLatch latch = new CountDownLatch(BURST_COUNT);
        
        for (int i = 0; i < BURST_COUNT; i++) {
            final int index = i;
            executorService.submit(() -> {
                try {
                    Response burstResponse = cancelService.cancelOrder(orderId, loginId, headers);
                    LOGGER.info("[sendBurstRequests][Burst request {} completed][Response: {}]", index, burstResponse);
                } catch (Exception e) {
                    LOGGER.error("[sendBurstRequests][Error in burst request {}][Error: {}]", index, e.getMessage());
                } finally {
                    latch.countDown();
                }
            });
        }

        try {
            latch.await(30, TimeUnit.SECONDS); // Wait for all burst requests to complete or timeout after 30 seconds
        } catch (InterruptedException e) {
            LOGGER.error("[sendBurstRequests][Interrupted while waiting for burst requests to complete]");
            Thread.currentThread().interrupt();
        }

        LOGGER.info("[sendBurstRequests][Burst completed.]");
        burstCounter.set(0);
    }

}
