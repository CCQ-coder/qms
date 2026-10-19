package com.pcbqms.integration.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

import com.pcbqms.integration.application.OcrWorkOrderImportService;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

class OcrWorkOrderImportControllerConditionTest {
    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
        .withBean(OcrWorkOrderImportService.class, () -> mock(OcrWorkOrderImportService.class))
        .withUserConfiguration(OcrWorkOrderImportController.class);

    @Test
    void doesNotRegisterOcrEndpointsWhenFeatureIsDisabled() {
        contextRunner.withPropertyValues("qms.ocr.enabled=false")
            .run(context -> assertThat(context).doesNotHaveBean(OcrWorkOrderImportController.class));
    }

    @Test
    void registersOcrEndpointsOnlyWhenExplicitlyEnabled() {
        contextRunner.withPropertyValues("qms.ocr.enabled=true")
            .run(context -> assertThat(context).hasSingleBean(OcrWorkOrderImportController.class));
    }
}
