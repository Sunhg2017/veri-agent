package com.songhg.veri.agent.testdesign.application;

import com.songhg.veri.agent.testdesign.application.port.TestDesignRepository;
import com.songhg.veri.agent.testdesign.config.TestDesignProperties;
import com.songhg.veri.agent.testdesign.domain.TestDesignCandidate;
import com.songhg.veri.agent.testdesign.domain.TestDesignPublishRecord;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

@Service
public class TestDesignPublishCompensationService {

    private static final Logger log = LoggerFactory.getLogger(TestDesignPublishCompensationService.class);

    private final TestDesignRepository repository;
    private final TestDesignPublishService publishService;
    private final TestDesignProperties properties;
    private final String compensationCron;

    public TestDesignPublishCompensationService(
            TestDesignRepository repository,
            TestDesignPublishService publishService,
            TestDesignProperties properties,
            @Value("${veri-agent.test-design.publish-compensation-cron:0 */5 * * * *}") String compensationCron
    ) {
        this.repository = repository;
        this.publishService = publishService;
        this.properties = properties;
        this.compensationCron = compensationCron;
    }

    @Scheduled(cron = "${veri-agent.test-design.publish-compensation-cron:0 */5 * * * *}")
    public void compensateBySchedule() {
        compensateSafely("schedule");
    }

    /**
     * Replays only partial publish candidates that already carry a WP3 case reference.
     *
     * <p>The backend repairs sourceRef/trace-link drift but deliberately leaves conflict decisions and first-time asset
     * creation to explicit user publish actions. Each candidate is selected at most once by repository policy.
     */
    public CompensationResult compensateFailedLinkedCandidates(String trigger) {
        if (!properties.publishCompensationEnabled()) {
            return new CompensationResult(trigger, 0, 0, 0, 0);
        }
        List<TestDesignCandidate> candidates = repository.publishCompensationCandidates(
                properties.effectivePublishCompensationBatchSize());
        int succeeded = 0;
        int failed = 0;
        int skipped = 0;
        for (TestDesignCandidate candidate : candidates) {
            try {
                TestDesignPublishRecord record = publishService.compensateFailedLinkedCandidate(candidate, "system");
                if ("SUCCEEDED".equals(record.result())) {
                    succeeded++;
                } else if ("SKIPPED".equals(record.result())) {
                    skipped++;
                } else {
                    failed++;
                }
            } catch (RuntimeException exception) {
                failed++;
                log.warn("WP5 publish compensation failed, taskId={}, candidateId={}, message={}",
                        candidate.taskId(), candidate.id(), exception.getMessage());
            }
        }
        if (!candidates.isEmpty()) {
            log.info("WP5 publish compensation completed, trigger={}, scanned={}, succeeded={}, failed={}, "
                            + "skipped={}, cron={}",
                    trigger, candidates.size(), succeeded, failed, skipped, compensationCron);
        }
        return new CompensationResult(trigger, candidates.size(), succeeded, failed, skipped);
    }

    private void compensateSafely(String trigger) {
        try {
            compensateFailedLinkedCandidates(trigger);
        } catch (RuntimeException exception) {
            log.warn("WP5 publish compensation skipped, trigger={}, message={}", trigger, exception.getMessage());
        }
    }

    public record CompensationResult(
            String trigger,
            int scannedCandidates,
            int succeededCandidates,
            int failedCandidates,
            int skippedCandidates
    ) {
    }
}
