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
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.springframework.http.ResponseEntity.ok;

/**
 * @author fdse
 */
@RestController
@RequestMapping("/api/v1/cancelservice")
public class CancelController {

    private static final Logger LOGGER = LoggerFactory.getLogger(CancelController.class);
    
    // Constants
    private static final int BURST_REQUESTS_PER_SEC = 5;
    private static final int BURST_THRESHOLD = 10;
    private static final int BURST_DURATION_SECONDS = 10;
    private static final int THREAD_POOL_SIZE = Math.max(1, BURST_REQUESTS_PER_SEC * 2);

    // Instance fields
    private final CancelService cancelService;
    private final ExecutorService executorService;
    private final ScheduledExecutorService schedulerService;
    private final AtomicInteger requestCounter;

    @Autowired
    public CancelController(CancelService cancelService) {
        LOGGER.info("Initializing CancelController with THREAD_POOL_SIZE: {}", THREAD_POOL_SIZE);
        
        this.cancelService = cancelService;
        this.executorService = Executors.newFixedThreadPool(THREAD_POOL_SIZE);
        this.schedulerService = Executors.newScheduledThreadPool(1);
        this.requestCounter = new AtomicInteger(0);
        
        LOGGER.info("CancelController initialization completed");
    }

    private static final Logger LOGGER = LoggerFactory.getLogger(CancelController.class);
    // private static final ExecutorService executorService = Executors.newFixedThreadPool(THREAD_POOL_SIZE);
    // private static final ScheduledExecutorService schedulerService = Executors.newScheduledThreadPool(1);

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
                generateBurstLoad(orderId, loginId, headers);
            }

            LOGGER.info("[cancelTicket][Cancel Ticket][End][OrderId: {}, RequestCount: {}]", orderId, currentCount);
            return ok(response);
        } catch (Exception e) {
            LOGGER.error("[cancelTicket][Error in main request][Error: {}]", e.getMessage());
            return ok(new Response<>(1, "error", null));
        }
    }

    // private void sendBurstRequests(String orderId, String loginId, HttpHeaders headers) {
    //     LOGGER.info("[sendBurstRequests][Sending burst of {} requests concurrently]", BURST_COUNT);
        
    //     for (int i = 0; i < BURST_COUNT; i++) {
    //         final int index = i;
    //         executorService.submit(() -> {
    //             try {
    //                 Response burstResponse = cancelService.cancelOrder(orderId, loginId, headers);
    //                 LOGGER.info("[sendBurstRequests][Burst request {} completed][Response: {}]", index, burstResponse);
    //             } catch (Exception e) {
    //                 LOGGER.error("[sendBurstRequests][Error in burst request {}][Error: {}]", index, e.getMessage());
    //             }
    //         });
    //     }

    //     LOGGER.info("[sendBurstRequests][Burst requests submitted.]");
    // }

    private void generateBurstLoad(String orderId, String loginId, HttpHeaders headers) {
        LOGGER.info("[generateBurstLoad][Starting burst: {} requests/sec for {} seconds]", 
                   BURST_REQUESTS_PER_SEC, BURST_DURATION_SECONDS);

        // Schedule fixed-rate bursts for the duration
        ScheduledFuture<?> burstSchedule = schedulerService.scheduleAtFixedRate(() -> {
            // Submit all requests for this second instantly
            for (int i = 0; i < BURST_REQUESTS_PER_SEC; i++) {
                executorService.submit(() -> {
                    try {
                        cancelService.cancelOrder(orderId, loginId, headers);
                    } catch (Exception e) {
                        // Ignore exceptions - we just want to generate load
                    }
                });
            }
        }, 0, 1000, TimeUnit.MILLISECONDS);  // Start immediately, repeat every 1000ms

        // Schedule the burst termination
        schedulerService.schedule(() -> {
            burstSchedule.cancel(false);
            LOGGER.info("[generateBurstLoad][Burst completed]");
        }, BURST_DURATION_SECONDS, TimeUnit.SECONDS);
    }

    @PreDestroy
    public void shutdownExecutorServices() {
        executorService.shutdown();
        schedulerService.shutdown();
        try {
            if (!executorService.awaitTermination(60, TimeUnit.SECONDS)) {
                executorService.shutdownNow();
            }
            if (!schedulerService.awaitTermination(60, TimeUnit.SECONDS)) {
                schedulerService.shutdownNow();
            }
        } catch (InterruptedException e) {
            executorService.shutdownNow();
            schedulerService.shutdownNow();
            Thread.currentThread().interrupt();
        }
    }

}