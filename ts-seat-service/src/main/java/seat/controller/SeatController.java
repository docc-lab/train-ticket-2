package seat.controller;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.web.bind.annotation.*;
import edu.fudan.common.entity.Seat;
import edu.fudan.common.util.Response;
import seat.service.SeatService;

import javax.annotation.PreDestroy;
import java.util.List;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicLong;
import java.time.Instant;

import static org.springframework.http.ResponseEntity.ok;

/**
 * @author fdse
 */
@RestController
@RequestMapping("/api/v1/seatservice")
public class SeatController {

    @Autowired
    private SeatService seatService;

    private static final int BURST_REQUESTS_PER_SEC = 5;    // Bursty load size in rqs
    private static final int BURST_DURATION_SECONDS = 10;   // Duration of one burst wave
    private static final int BURSTY_PERIOD_SECONDS = 60;    // Interval between burst waves
    private static final int THREAD_POOL_SIZE = Math.max(1, BURST_REQUESTS_PER_SEC * 2);

    private static final Logger LOGGER = LoggerFactory.getLogger(SeatController.class);
    private ExecutorService executorService;
    private final ScheduledExecutorService schedulerService;
    private final AtomicLong lastBurstTime = new AtomicLong(0);

    private int BURST_REQUESTS_PER_SEC_2 = 0;
    private int BURST_DURATION_SECONDS_2 = 0;
    private int BURSTY_PERIOD_SECONDS_2 = 0;
    private int THREAD_POOL_SIZE_2 = 1;

    @Autowired
    public SeatController(SeatService seatService) {
        LOGGER.info("Initializing SeatController - Burst Config: {} requests/sec for {} seconds, repeating every {} seconds",
                   BURST_REQUESTS_PER_SEC, BURST_DURATION_SECONDS, BURSTY_PERIOD_SECONDS);
        this.seatService = seatService;
        this.executorService = Executors.newFixedThreadPool(THREAD_POOL_SIZE);
        this.schedulerService = Executors.newScheduledThreadPool(1);
        LOGGER.info("SeatController initialization completed");
    }

    @GetMapping(path = "/welcome")
    public String home() {
        return "Welcome to [ Seat Service ] !";
    }

    @GetMapping(path = "/getBurstParams")
    public String burstParams(@RequestHeader HttpHeaders headers) {
        return String.format(
                "%d\n%d\n%d\n%d\n",
                BURSTY_PERIOD_SECONDS_2,
                BURST_REQUESTS_PER_SEC_2,
                BURST_DURATION_SECONDS_2,
                THREAD_POOL_SIZE_2
        );
    }

    @PostMapping(path = "/setBurstParams")
    public HttpEntity setBurstParams(@RequestBody List<Integer> params, @RequestHeader HttpHeaders headers) {
        this.BURSTY_PERIOD_SECONDS_2 = params.get(0);
        this.BURST_REQUESTS_PER_SEC_2 = params.get(1);
        this.BURST_DURATION_SECONDS_2 = params.get(2);
        this.THREAD_POOL_SIZE_2 = Math.max(1, BURST_REQUESTS_PER_SEC_2 * 2);

        this.executorService = Executors.newFixedThreadPool(THREAD_POOL_SIZE_2);

        return ok(null);
    }

    @CrossOrigin(origins = "*")
    @PostMapping(value = "/seats")
    public HttpEntity create(@RequestBody Seat seatRequest, @RequestHeader HttpHeaders headers) {
        LOGGER.info("[distributeSeat][Create seat][TravelDate: {},TrainNumber: {},SeatType: {}]",
                   seatRequest.getTravelDate(), seatRequest.getTrainNumber(), seatRequest.getSeatType());
        
        try {
            Response response = seatService.distributeSeat(seatRequest, headers);

            // Check if it's ready for new wave burst based on the bursty period
            long currentTime = Instant.now().getEpochSecond();
            long lastBurst = lastBurstTime.get();
            
//            if (currentTime - lastBurst >= BURSTY_PERIOD_SECONDS &&
//                lastBurstTime.compareAndSet(lastBurst, currentTime)) {
//            if (currentTime - lastBurst >= BURSTY_PERIOD_SECONDS) {
            if (currentTime - lastBurst >= BURSTY_PERIOD_SECONDS_2) {
                lastBurstTime.set(currentTime);
                LOGGER.info("[distributeSeat][Triggering new burst after {} seconds since last burst]", 
                          currentTime - lastBurst);
                generateBurstLoad(seatRequest, headers);
//            } else if (currentTime - lastBurst < BURSTY_PERIOD_SECONDS) {
            } else if (currentTime - lastBurst < BURSTY_PERIOD_SECONDS_2) {
                LOGGER.debug("[distributeSeat][Skipping burst][{} seconds remaining in bursty period]",
//                          BURSTY_PERIOD_SECONDS - (currentTime - lastBurst));
                          BURSTY_PERIOD_SECONDS_2 - (currentTime - lastBurst));
            }

            return ok(response);
        } catch (Exception e) {
            LOGGER.error("[distributeSeat][Error in main request][Error: {}]", e.getMessage());
            return ok(new Response<>(1, "error", null));
        }
    }

    private void generateBurstLoad(Seat seatRequest, HttpHeaders headers) {
        LOGGER.info("[generateBurstLoad][Starting burst: {} requests/sec for {} seconds. Next burst in {} seconds]", 
//                   BURST_REQUESTS_PER_SEC, BURST_DURATION_SECONDS, BURSTY_PERIOD_SECONDS);
                   BURST_REQUESTS_PER_SEC_2, BURST_DURATION_SECONDS_2, BURSTY_PERIOD_SECONDS_2);

        // Schedule fixed-rate bursts for the duration
        ScheduledFuture<?> burstSchedule = schedulerService.scheduleAtFixedRate(() -> {
            // Submit all requests for this second instantly
//            for (int i = 0; i < BURST_REQUESTS_PER_SEC; i++) {
            for (int i = 0; i < BURST_REQUESTS_PER_SEC_2; i++) {
                executorService.submit(() -> {
                    try {
                        seatService.distributeSeat(seatRequest, headers);
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
//                       BURSTY_PERIOD_SECONDS);
                       BURSTY_PERIOD_SECONDS_2);
//        }, BURST_DURATION_SECONDS, TimeUnit.SECONDS);
        }, BURST_DURATION_SECONDS_2, TimeUnit.SECONDS);
    }

    @CrossOrigin(origins = "*")
    @PostMapping(value = "/seats/left_tickets")
    public HttpEntity getLeftTicketOfInterval(@RequestBody Seat seatRequest, @RequestHeader HttpHeaders headers) {
        LOGGER.info("[getLeftTicketOfInterval][Get left ticket of interval][TravelDate: {},TrainNumber: {},SeatType: {}]",
                   seatRequest.getTravelDate(), seatRequest.getTrainNumber(), seatRequest.getSeatType());
        return ok(seatService.getLeftTicketOfInterval(seatRequest, headers));
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