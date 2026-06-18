package com.songhg.veri.agent.testdesign.application;

import com.songhg.veri.agent.testdesign.application.port.TestDesignRepository;
import com.songhg.veri.agent.testdesign.config.TestDesignProperties;
import com.songhg.veri.agent.testdesign.domain.TestDesignCandidate;
import com.songhg.veri.agent.testdesign.domain.TestDesignPublishRecord;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

@Service
public class TestDesignPublishCompensationService {

    private static final Logger log = LoggerFactory.getLogger(TestDesignPublishCompensationService.class);

    private final TestDesignRepository repository;
    private final TestDesignPublishService publishService;
    private final TestDesignProperties properties;
    @Autowired
    public TestDesignPublishCompensationService(
            TestDesignRepository repository,
            TestDesignPublishService publishService,
            TestDesignProperties properties
    ) {
        this.repository = repository;
        this.publishService = publishService;
        this.properties = properties;
    }

    TestDesignPublishCompensationService(
            TestDesignRepository repository,
            TestDesignPublishService publishService,
            TestDesignProperties properties,
            String ignoredCompensationCron
    ) {
        this(repository, publishService, properties);
    }

    /**
     * Keeps the legacy manual entry point so tests and ad-hoc maintenance can still reuse the safe wrapper.
     */
    public void compensateBySchedule() {
        try {
            compensateFailedLinkedCandidates("schedule");
        } catch (RuntimeException exception) {
            log.warn("WP5 publish compensation skipped, trigger={}, message={}", "schedule", exception.getMessage());
        }
    }

    /**
     * Replays only partial publish candidates that already carry a WP3 case reference.
     *
     * <p>The backend repairs sourceRef/trace-link drift but deliberately leaves conflict decisions and first-time asset
     * creation to explicit user publish actions. Each candidate is selected at most once by repository policy.
     */
    public CompensationResult compensateFailedLinkedCandidates(String trigger) {
        return compensateFailedLinkedCandidates(
                trigger,
                null,
                null,
                properties.effectivePublishCompensationBatchSize()
        );
    }

    /**
     * Replays partial publish candidates inside a bounded project/prompt scope.
     *
     * <p>This manual entry point uses the same repository eligibility policy as the scheduled backend: only FAILED
     * candidates with an existing WP3 case reference, no successful publish record and no previous automatic
     * compensation attempt can be selected. The caller receives aggregate counts only.</p>
     */
    public CompensationResult compensateFailedLinkedCandidates(
            String trigger,
            String projectId,
            String promptKey,
            int maxItems
    ) {
        if (!properties.publishCompensationEnabled()) {
            return new CompensationResult(trigger, 0, 0, 0, 0);
        }
        List<TestDesignCandidate> candidates = repository.publishCompensationCandidates(
                projectId,
                promptKey,
                safeCompensationLimit(maxItems)
        );
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
            log.info("WP5 publish compensation completed, trigger={}, scanned={}, succeeded={}, failed={}, skipped={}",
                    trigger, candidates.size(), succeeded, failed, skipped);
        }
        return new CompensationResult(trigger, candidates.size(), succeeded, failed, skipped);
    }

    private int safeCompensationLimit(int maxItems) {
        if (maxItems <= 0) {
            return properties.effectivePublishCompensationBatchSize();
        }
        return Math.min(100, maxItems);
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
