package com.songhg.veri.agent.scheduling.application;

import com.songhg.veri.agent.testdesign.application.TestDesignEventRecoveryService;
import com.songhg.veri.agent.testdesign.application.TestDesignPublishCompensationService;
import com.songhg.veri.agent.testdesign.application.TestDesignPublishEventRecoveryService;
import com.xxl.job.core.handler.annotation.XxlJob;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/**
 * XXL-JOB handlers for WP5 async generation/publish recovery and publish compensation.
 */
@Component
@ConditionalOnProperty(prefix = "veri-agent.xxl-job", name = "enabled", havingValue = "true")
public class TestDesignJobHandler {

    private final TestDesignEventRecoveryService testDesignEventRecoveryService;
    private final TestDesignPublishEventRecoveryService testDesignPublishEventRecoveryService;
    private final TestDesignPublishCompensationService testDesignPublishCompensationService;

    public TestDesignJobHandler(
            TestDesignEventRecoveryService testDesignEventRecoveryService,
            TestDesignPublishEventRecoveryService testDesignPublishEventRecoveryService,
            TestDesignPublishCompensationService testDesignPublishCompensationService
    ) {
        this.testDesignEventRecoveryService = testDesignEventRecoveryService;
        this.testDesignPublishEventRecoveryService = testDesignPublishEventRecoveryService;
        this.testDesignPublishCompensationService = testDesignPublishCompensationService;
    }

    @XxlJob("testDesignEventRecoveryJob")
    public void testDesignEventRecoveryJob() throws Exception {
        XxlJobTraceSupport.execute("testDesignEventRecoveryJob", () -> {
            testDesignEventRecoveryService.recoverQueuedEvents("xxl-job");
            return null;
        });
    }

    @XxlJob("testDesignPublishEventRecoveryJob")
    public void testDesignPublishEventRecoveryJob() throws Exception {
        XxlJobTraceSupport.execute("testDesignPublishEventRecoveryJob", () -> {
            testDesignPublishEventRecoveryService.recoverQueuedPublishes("xxl-job");
            return null;
        });
    }

    @XxlJob("testDesignPublishCompensationJob")
    public void testDesignPublishCompensationJob() throws Exception {
        XxlJobTraceSupport.execute("testDesignPublishCompensationJob", () -> {
            testDesignPublishCompensationService.compensateFailedLinkedCandidates("xxl-job");
            return null;
        });
    }
}
