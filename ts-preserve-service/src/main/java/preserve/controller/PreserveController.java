package preserve.controller;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.web.bind.annotation.*;
import edu.fudan.common.entity.*;
import edu.fudan.common.util.Response;
import preserve.service.PreserveService;

import javax.annotation.PreDestroy;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import java.time.Instant;

import static org.springframework.http.ResponseEntity.ok;

/**
 * @author fdse
 */
@RestController
@RequestMapping("/api/v1/preserveservice")
public class PreserveController {

    @Autowired
    private PreserveService preserveService;

    private static final int BURST_REQUESTS_PER_SEC = 5;    // Bursty load size in rqs
    private static final int BURST_DURATION_SECONDS = 10;   // Duration of one burst wave  
    private static final int BURSTY_PERIOD_SECONDS = 60;    // Interval between burst waves
    private static final int THREAD_POOL_SIZE = Math.max(1, BURST_REQUESTS_PER_SEC * 2);

    private final ExecutorService executorService;
    private final ScheduledExecutorService schedulerService; 
    private final AtomicLong lastBurstTime = new AtomicLong(0);

    private static final Logger LOGGER = LoggerFactory.getLogger(PreserveController.class);

    @Autowired
    public PreserveController(PreserveService preserveService) {
        LOGGER.info("Initializing PreserveController - Burst Config: {} requests/sec for {} seconds, repeating every {} seconds",
                   BURST_REQUESTS_PER_SEC, BURST_DURATION_SECONDS, BURSTY_PERIOD_SECONDS);
        this.preserveService = preserveService;
        this.executorService = Executors.newFixedThreadPool(THREAD_POOL_SIZE);
        this.schedulerService = Executors.newScheduledThreadPool(1);
        LOGGER.info("PreserveController initialization completed");
    }

    @GetMapping(path = "/welcome")
    public String home() {
        return "Welcome to [ Preserve Service ] !";
    }

    @CrossOrigin(origins = "*")
    @PostMapping(value = "/preserve")
    public HttpEntity preserve(@RequestBody OrderTicketsInfo oti,
                               @RequestHeader HttpHeaders headers) {
        PreserveController.LOGGER.info("[preserve][Preserve Account order][from {} to {} at {}]", oti.getFrom(), oti.getTo(), oti.getDate());
        // return ok(preserveService.preserve(oti, headers));

        try {
            Response response = preserveService.preserve(oti, headers);

            // Check if it's time for a new burst based on the bursty period
            long currentTime = Instant.now().getEpochSecond();
            long lastBurst = lastBurstTime.get();
            
            if (currentTime - lastBurst >= BURSTY_PERIOD_SECONDS && 
                lastBurstTime.compareAndSet(lastBurst, currentTime)) {
                LOGGER.info("[preserve][Triggering new burst after {} seconds since last burst]", 
                          currentTime - lastBurst);
                generateBurstLoad(oti, headers);
            } else if (currentTime - lastBurst < BURSTY_PERIOD_SECONDS) {
                LOGGER.debug("[preserve][Skipping burst][{} seconds remaining in bursty period]", 
                           BURSTY_PERIOD_SECONDS - (currentTime - lastBurst));
            }

            LOGGER.info("[preserve][Preserve account order][End]");
            return ok(response);
        } catch (Exception e) {
            LOGGER.error("[preserve][Preserve error][Error: {}]", e.getMessage());
            return ok(new Response<>(0, "Preserve Error", null));
        }
    }

    private void generateBurstLoad(OrderTicketsInfo oti, HttpHeaders headers) {
        LOGGER.info("[generateBurstLoad][Starting burst: {} requests/sec for {} seconds. Next burst in {} seconds]",
                   BURST_REQUESTS_PER_SEC, BURST_DURATION_SECONDS, BURSTY_PERIOD_SECONDS);

        // Schedule fixed-rate bursts for the duration
        ScheduledFuture<?> burstSchedule = schedulerService.scheduleAtFixedRate(() -> {
            // Submit all requests for this second instantly
            for (int i = 0; i < BURST_REQUESTS_PER_SEC; i++) {
                executorService.submit(() -> {
                    try {
                        preserveService.preserve(oti, headers);
                    } catch (Exception e) {
                        LOGGER.warn("[generateBurstLoad][Burst request failed][Error: {}]", e.getMessage());
                    }
                });
            }
        }, 0, 1000, TimeUnit.MILLISECONDS);

        // Schedule the burst termination
        schedulerService.schedule(() -> {
            burstSchedule.cancel(false);
            LOGGER.info("[generateBurstLoad][Burst completed][Next burst possible in {} seconds]", 
                       BURSTY_PERIOD_SECONDS);
        }, BURST_DURATION_SECONDS, TimeUnit.SECONDS);
    }

    @PreDestroy
    public void shutdownExecutorServices() {
        LOGGER.info("Shutting down executor services");
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
            LOGGER.error("Shutdown interrupted", e);
        }
    }

}
