package org.prebid.server.hooks.modules.com.scientiamobile.wurfl.devicedetection.model;

import com.scientiamobile.wurfl.core.GeneralWURFLEngine;
import com.scientiamobile.wurfl.core.WURFLEngine;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.junit.jupiter.MockitoExtension;
import org.prebid.server.hooks.modules.com.scientiamobile.wurfl.devicedetection.config.WURFLDeviceDetectionConfigProperties;
import org.prebid.server.hooks.modules.com.scientiamobile.wurfl.devicedetection.exc.WURFLModuleConfigurationException;
import org.junit.jupiter.api.function.Executable;

import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mock.Strictness.LENIENT;
import static org.mockito.Mockito.mockStatic;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class WURFLEngineInitializerTest {

    @Mock(strictness = LENIENT)
    private WURFLDeviceDetectionConfigProperties configProperties;

    @Mock(strictness = LENIENT)
    private WURFLEngine wurflEngine;

    @BeforeEach
    void setUp() {
        when(configProperties.getWurflSnapshotUrl()).thenReturn("http://test.url/wurfl.zip");
        when(configProperties.getWurflFileDirPath()).thenReturn("/test/path");
    }

    @Test
    void downloadWurflFileIfNeededShouldDownloadWhenUrlAndPathArePresent() {
        try (MockedStatic<GeneralWURFLEngine> mockedStatic = mockStatic(GeneralWURFLEngine.class)) {
            // when
            WURFLEngineInitializer.downloadWurflFile(configProperties);

            // then
            mockedStatic.verify(() ->
                    GeneralWURFLEngine.wurflDownload("http://test.url/wurfl.zip", "/test/path"));
        }
    }

    @Test
    void verifyStaticCapabilitiesDefinitionShouldThrowExceptionWhenCapabilitiesAreNotDefined() {
        // given
        when(wurflEngine.getAllCapabilities()).thenReturn(Set.of(
                "brand_name",
                "density_class",
                "is_connected_tv",
                "is_ott",
                "is_tablet",
                "model_name"));

        final String expFailedCheckMessage = """
                            Static capabilities  %s needed for device enrichment are not defined in WURFL.
                            Please make sure that your license has the needed capabilities or upgrade it.
                """.formatted(String.join(",", List.of(
                "ajax_support_javascript",
                "physical_form_factor",
                "resolution_height",
                "resolution_width"
        )));

        // when
        final Executable exceptionSource = () -> WURFLEngineInitializer.verifyStaticCapabilitiesDefinition(wurflEngine);

        // then
        final Exception exception = assertThrows(WURFLModuleConfigurationException.class, exceptionSource);
        assertThat(exception.getMessage()).isEqualTo(expFailedCheckMessage);
    }

    @Test
    void verifyStaticCapabilitiesDefinitionShouldCompleteSuccessfullyWhenCapabilitiesAreDefined() {
        // given
        when(wurflEngine.getAllCapabilities()).thenReturn(Set.of(
                "brand_name",
                "density_class",
                "is_connected_tv",
                "is_ott",
                "is_tablet",
                "model_name",
                "resolution_width",
                "resolution_height",
                "physical_form_factor",
                "ajax_support_javascript"
        ));

        // when
        var excOccurred = false;
        try {
            WURFLEngineInitializer.verifyStaticCapabilitiesDefinition(wurflEngine);
        } catch (Exception e) {
            excOccurred = true;
        }

        // then
        assertThat(excOccurred).isFalse();
    }

    @Test
    void builderShouldCreateWURFLEngineInitializerBuilderFromProperties() {
        // given
        when(configProperties.getWurflSnapshotUrl()).thenReturn("http://test.url/wurfl.zip");
        when(configProperties.getWurflFileDirPath()).thenReturn("/test/path");
        when(configProperties.getCacheSize()).thenReturn(1000);
        when(configProperties.isWurflRunUpdater()).thenReturn(true);

        // when
        final var builder = WURFLEngineInitializer.builder()
                .configProperties(configProperties);

        // then
        assertThat(builder).isNotNull();
        assertThat(builder.build()).isNotNull();
        assertThat(builder.toString()).isNotEmpty();
    }
}
