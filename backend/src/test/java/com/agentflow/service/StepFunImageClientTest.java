package com.agentflow.service;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class StepFunImageClientTest {

    private final StepFunImageClient client = new StepFunImageClient();

    @Test
    void normalizeImageEndpoint_StepImageEdit2_UsesStepPlanPath() {
        String endpoint = client.normalizeImageEndpoint("https://api.stepfun.com/v1", "step-image-edit-2");

        assertEquals("https://api.stepfun.com/step_plan/v1/images/generations", endpoint);
    }

    @Test
    void normalizeSize_StepImageEdit2_MapsOpenPlatformWideSizeToStepPlanSize() {
        String size = client.normalizeSize("step-image-edit-2", "1280x800");

        assertEquals("768x1360", size);
    }

    @Test
    void normalizeDefaults_StepImageEdit2_UsesOfficialDefaults() {
        assertEquals(8, client.normalizeSteps("step-image-edit-2", 0));
        assertEquals(1.0, client.normalizeCfgScale("step-image-edit-2", 0));
    }
}
