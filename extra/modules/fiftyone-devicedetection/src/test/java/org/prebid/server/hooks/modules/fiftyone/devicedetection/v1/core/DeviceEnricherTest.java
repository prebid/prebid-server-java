package org.prebid.server.hooks.modules.fiftyone.devicedetection.v1.core;

import com.fasterxml.jackson.databind.node.TextNode;
import com.iab.openrtb.request.Device;
import fiftyone.devicedetection.shared.DeviceData;
import fiftyone.pipeline.core.data.FlowData;
import fiftyone.pipeline.core.flowelements.Pipeline;
import fiftyone.pipeline.engines.data.AspectPropertyValue;
import fiftyone.pipeline.engines.exceptions.NoValueException;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnit;
import org.mockito.junit.MockitoRule;
import org.prebid.server.hooks.modules.fiftyone.devicedetection.model.boundary.CollectedEvidence;
import org.prebid.server.proto.openrtb.ext.request.ExtDevice;

import java.math.BigDecimal;
import java.util.AbstractMap;
import java.util.Collections;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.prebid.server.hooks.modules.fiftyone.devicedetection.v1.core.DeviceEnricher.EXT_DEVICE_ID_KEY;
import static org.prebid.server.hooks.modules.fiftyone.devicedetection.v1.core.DeviceEnricher.getDeviceId;

public class DeviceEnricherTest {
    @Rule
    public final MockitoRule mockitoRule = MockitoJUnit.rule();

    @Mock
    private Pipeline pipeline;

    @Mock
    private FlowData flowData;

    @Mock
    private DeviceData deviceData;

    private DeviceEnricher target;

    @Before
    public void populateDeviceInfoSetUp() {
        when(pipeline.createFlowData()).thenReturn(flowData);
        when(flowData.get(DeviceData.class)).thenReturn(deviceData);
        target = new DeviceEnricher(pipeline);
    }

    // MARK: - populateDeviceInfo

    @Test
    public void populateDeviceInfoShouldReportErrorWhenPipelineThrowsException() throws Exception {
        // given
        final Exception e = new RuntimeException();
        when(pipeline.createFlowData()).thenThrow(e);

        // when
        final EnrichmentResult result = target.populateDeviceInfo(null, null);

        // then
        assertThat(result.processingException()).isEqualTo(e);
    }

    @Test
    public void populateDeviceInfoShouldReportErrorWhenProcessThrowsException() throws Exception {
        // given
        final Exception e = new RuntimeException();
        doThrow(e).when(flowData).process();
        final CollectedEvidence collectedEvidence = CollectedEvidence.builder().build();

        // when
        final EnrichmentResult result = target.populateDeviceInfo(
                null,
                collectedEvidence);

        // then
        assertThat(result.processingException()).isEqualTo(e);
    }

    @Test
    public void populateDeviceInfoShouldReturnNullWhenDeviceDataIsNull() throws Exception {
        // given
        final Exception e = new RuntimeException();
        when(flowData.get(DeviceData.class)).thenReturn(null);
        final CollectedEvidence collectedEvidence = CollectedEvidence.builder().build();

        // when
        final EnrichmentResult result = target.populateDeviceInfo(
                null,
                collectedEvidence);

        // then
        assertThat(result).isNull();
        verify(flowData, times(1)).get(DeviceData.class);
    }

    // MARK: - pickRelevantFrom

    @Test
    public void populateDeviceInfoShouldPassToFlowDataHeadersMadeFromSuaWhenPresent() throws Exception {
        // given
        final Map<String, String> secureHeaders = Collections.singletonMap("ua", "fake-ua");
        final CollectedEvidence collectedEvidence = CollectedEvidence.builder()
                .secureHeaders(secureHeaders)
                .rawHeaders(Collections.singletonMap("ua", "zumba").entrySet())
                .build();

        // when
        target.populateDeviceInfo(null, collectedEvidence);

        // then
        final ArgumentCaptor<Map<String, String>> evidenceCaptor = ArgumentCaptor.forClass(Map.class);
        verify(flowData).addEvidence(evidenceCaptor.capture());
        final Map<String, String> evidence = evidenceCaptor.getValue();

        assertThat(evidence).isNotSameAs(secureHeaders);
        assertThat(evidence).containsExactlyEntriesOf(secureHeaders);
    }

    @Test
    public void populateDeviceInfoShouldPassToFlowDataHeadersMadeFromUaWhenNoSuaPresent() throws Exception {
        // given
        final CollectedEvidence collectedEvidence = CollectedEvidence.builder()
                .deviceUA("dummy-ua")
                .rawHeaders(Collections.singletonMap("ua", "zumba").entrySet())
                .build();

        // when
        target.populateDeviceInfo(null, collectedEvidence);

        // then
        final ArgumentCaptor<Map<String, String>> evidenceCaptor = ArgumentCaptor.forClass(Map.class);
        verify(flowData).addEvidence(evidenceCaptor.capture());
        final Map<String, String> evidence = evidenceCaptor.getValue();

        assertThat(evidence.size()).isEqualTo(1);
        final Map.Entry<String, String> evidenceFragment = evidence.entrySet().stream().findFirst().get();
        assertThat(evidenceFragment.getKey()).isEqualTo("header.user-agent");
        assertThat(evidenceFragment.getValue()).isEqualTo(collectedEvidence.deviceUA());
    }

    @Test
    public void populateDeviceInfoShouldPassToFlowDataMergedHeadersMadeFromUaAndSuaWhenBothPresent() throws Exception {
        // given
        final Map<String, String> suaHeaders = Collections.singletonMap("ua", "fake-ua");
        final CollectedEvidence collectedEvidence = CollectedEvidence.builder()
                .secureHeaders(suaHeaders)
                .deviceUA("dummy-ua")
                .rawHeaders(Collections.singletonMap("ua", "zumba").entrySet())
                .build();

        // when
        target.populateDeviceInfo(null, collectedEvidence);

        // then
        final ArgumentCaptor<Map<String, String>> evidenceCaptor = ArgumentCaptor.forClass(Map.class);
        verify(flowData).addEvidence(evidenceCaptor.capture());
        final Map<String, String> evidence = evidenceCaptor.getValue();

        assertThat(evidence).isNotEqualTo(suaHeaders);
        assertThat(evidence).containsAllEntriesOf(suaHeaders);
        assertThat(evidence).containsEntry("header.user-agent", collectedEvidence.deviceUA());
        assertThat(evidence.size()).isEqualTo(suaHeaders.size() + 1);
    }

    @Test
    public void populateDeviceInfoShouldPassToFlowDataRawHeaderWhenNoDeviceInfoPresent() throws Exception {
        // given
        final List<Map.Entry<String, String>> rawHeaders = List.of(
                new AbstractMap.SimpleEntry<>("ua", "zumba"),
                new AbstractMap.SimpleEntry<>("sec-ua", "astrolabe")
        );
        final CollectedEvidence collectedEvidence = CollectedEvidence.builder()
                .rawHeaders(rawHeaders)
                .build();

        // when
        target.populateDeviceInfo(null, collectedEvidence);

        // then
        final ArgumentCaptor<Map<String, String>> evidenceCaptor = ArgumentCaptor.forClass(Map.class);
        verify(flowData).addEvidence(evidenceCaptor.capture());
        final Map<String, String> evidence = evidenceCaptor.getValue();

        final List<Map.Entry<String, String>> evidenceFragments = evidence.entrySet().stream().toList();
        assertThat(evidenceFragments.size()).isEqualTo(rawHeaders.size());
        for (int i = 0, n = rawHeaders.size(); i < n; ++i) {
            final Map.Entry<String, String> rawEntry = rawHeaders.get(i);
            final Map.Entry<String, String> newEntry = evidenceFragments.get(i);
            assertThat(newEntry.getKey()).isEqualTo("header." + rawEntry.getKey());
            assertThat(newEntry.getValue()).isEqualTo(rawEntry.getValue());
        }
    }

    @Test
    public void populateDeviceInfoShouldPassToFlowDataLatestRawHeaderWhenMultiplePresentWithSameKey() throws Exception {
        // given
        final String theKey = "ua";
        final List<Map.Entry<String, String>> rawHeaders = List.of(
                new AbstractMap.SimpleEntry<>(theKey, "zumba"),
                new AbstractMap.SimpleEntry<>(theKey, "astrolabe")
        );
        final CollectedEvidence collectedEvidence = CollectedEvidence.builder()
                .rawHeaders(rawHeaders)
                .build();

        // when
        target.populateDeviceInfo(null, collectedEvidence);

        // then
        final ArgumentCaptor<Map<String, String>> evidenceCaptor = ArgumentCaptor.forClass(Map.class);
        verify(flowData).addEvidence(evidenceCaptor.capture());
        final Map<String, String> evidence = evidenceCaptor.getValue();

        final List<Map.Entry<String, String>> evidenceFragments = evidence.entrySet().stream().toList();
        assertThat(evidenceFragments.size()).isEqualTo(1);
        assertThat(evidenceFragments.get(0).getValue()).isEqualTo(rawHeaders.get(1).getValue());
    }

    @Test
    public void populateDeviceInfoShouldPassToFlowDataEmptyMapWhenNoEvidenceToPick() throws Exception {
        // given
        final CollectedEvidence collectedEvidence = CollectedEvidence.builder().build();

        // when
        target.populateDeviceInfo(null, collectedEvidence);

        // then
        final ArgumentCaptor<Map<String, String>> evidenceCaptor = ArgumentCaptor.forClass(Map.class);
        verify(flowData).addEvidence(evidenceCaptor.capture());
        final Map<String, String> evidence = evidenceCaptor.getValue();

        assertThat(evidence).isNotNull();
        assertThat(evidence).isEmpty();
    }

    // MARK: - patchDevice

    @Test
    public void populateDeviceInfoShouldEnrichAllPropertiesWhenDeviceIsEmpty() throws Exception {
        // given
        final Device device = Device.builder().build();

        // when
        buildCompleteDeviceData();
        final CollectedEvidence collectedEvidence = CollectedEvidence.builder()
                .deviceUA("fake-UserAgent")
                .build();
        final EnrichmentResult result = target.populateDeviceInfo(device, collectedEvidence);

        // then
        assertThat(result.enrichedFields()).hasSize(10);
    }

    @Test
    public void populateDeviceInfoShouldReturnNullWhenDeviceIsFull() throws Exception {
        // given and when
        buildCompleteDeviceData();
        final Device device = buildCompleteDevice();
        final CollectedEvidence collectedEvidence = CollectedEvidence.builder()
                .deviceUA("fake-UserAgent")
                .build();
        final EnrichmentResult result = target.populateDeviceInfo(device, collectedEvidence);

        // then
        assertThat(result).isNull();
    }

    @Test
    public void populateDeviceInfoShouldEnrichDeviceTypeWhenItIsMissing() throws Exception {
        // given
        final Device testDevice = buildCompleteDevice().toBuilder()
                .devicetype(null)
                .build();

        // when
        buildCompleteDeviceData();
        final CollectedEvidence collectedEvidence = CollectedEvidence.builder()
                .deviceUA("fake-UserAgent")
                .build();
        final EnrichmentResult result = target.populateDeviceInfo(testDevice, collectedEvidence);

        // then
        assertThat(result.enrichedFields()).hasSize(1);
        assertThat(result.enrichedDevice().getDevicetype()).isEqualTo(buildCompleteDevice().getDevicetype());
    }

    @Test
    public void populateDeviceInfoShouldEnrichMakeWhenItIsMissing() throws Exception {
        // given
        final Device testDevice = buildCompleteDevice().toBuilder()
                .make(null)
                .build();

        // when
        buildCompleteDeviceData();
        final CollectedEvidence collectedEvidence = CollectedEvidence.builder()
                .deviceUA("fake-UserAgent")
                .build();
        final EnrichmentResult result = target.populateDeviceInfo(testDevice, collectedEvidence);

        // then
        assertThat(result.enrichedFields()).hasSize(1);
        assertThat(result.enrichedDevice().getMake()).isEqualTo(buildCompleteDevice().getMake());
    }

    @Test
    public void populateDeviceInfoShouldEnrichModelWithHWNameWhenHWModelIsMissing() throws Exception {
        // given
        final Device testDevice = buildCompleteDevice().toBuilder()
                .model(null)
                .build();
        final String expectedModel = "NinjaTech8888";
        when(deviceData.getHardwareName())
                .thenReturn(aspectPropertyValueWith(Collections.singletonList(expectedModel)));
        when(deviceData.getHardwareModel()).thenThrow(new RuntimeException());

        // when
        final CollectedEvidence collectedEvidence = CollectedEvidence.builder()
                .deviceUA("fake-UserAgent")
                .build();
        final EnrichmentResult result = target.populateDeviceInfo(testDevice, collectedEvidence);

        // then
        assertThat(result.enrichedFields()).hasSize(1);
        assertThat(result.enrichedDevice().getModel()).isEqualTo(expectedModel);
    }

    @Test
    public void populateDeviceInfoShouldEnrichModelWhenItIsMissing() throws Exception {
        // given
        final Device testDevice = buildCompleteDevice().toBuilder()
                .model(null)
                .build();

        // when
        buildCompleteDeviceData();
        final CollectedEvidence collectedEvidence = CollectedEvidence.builder()
                .deviceUA("fake-UserAgent")
                .build();
        final EnrichmentResult result = target.populateDeviceInfo(testDevice, collectedEvidence);

        // then
        assertThat(result.enrichedFields()).hasSize(1);
        assertThat(result.enrichedDevice().getModel()).isEqualTo(buildCompleteDevice().getModel());
    }

    @Test
    public void populateDeviceInfoShouldEnrichOsWhenItIsMissing() throws Exception {
        // given
        final Device testDevice = buildCompleteDevice().toBuilder()
                .os(null)
                .build();

        // when
        buildCompleteDeviceData();
        final CollectedEvidence collectedEvidence = CollectedEvidence.builder()
                .deviceUA("fake-UserAgent")
                .build();
        final EnrichmentResult result = target.populateDeviceInfo(testDevice, collectedEvidence);

        // then
        assertThat(result.enrichedFields()).hasSize(1);
        assertThat(result.enrichedDevice().getOs()).isEqualTo(buildCompleteDevice().getOs());
    }

    @Test
    public void populateDeviceInfoShouldEnrichOsvWhenItIsMissing() throws Exception {
        // given
        final Device testDevice = buildCompleteDevice().toBuilder()
                .osv(null)
                .build();

        // when
        buildCompleteDeviceData();
        final CollectedEvidence collectedEvidence = CollectedEvidence.builder()
                .deviceUA("fake-UserAgent")
                .build();
        final EnrichmentResult result = target.populateDeviceInfo(testDevice, collectedEvidence);

        // then
        assertThat(result.enrichedFields()).hasSize(1);
        assertThat(result.enrichedDevice().getOsv()).isEqualTo(buildCompleteDevice().getOsv());
    }

    @Test
    public void populateDeviceInfoShouldEnrichHWhenItIsMissing() throws Exception {
        // given
        final Device testDevice = buildCompleteDevice().toBuilder()
                .h(null)
                .build();

        // when
        buildCompleteDeviceData();
        final CollectedEvidence collectedEvidence = CollectedEvidence.builder()
                .deviceUA("fake-UserAgent")
                .build();
        final EnrichmentResult result = target.populateDeviceInfo(testDevice, collectedEvidence);

        // then
        assertThat(result.enrichedFields()).hasSize(1);
        assertThat(result.enrichedDevice().getH()).isEqualTo(buildCompleteDevice().getH());
    }

    @Test
    public void populateDeviceInfoShouldEnrichWWhenItIsMissing() throws Exception {
        // given
        final Device testDevice = buildCompleteDevice().toBuilder()
                .w(null)
                .build();

        // when
        buildCompleteDeviceData();
        final CollectedEvidence collectedEvidence = CollectedEvidence.builder()
                .deviceUA("fake-UserAgent")
                .build();
        final EnrichmentResult result = target.populateDeviceInfo(testDevice, collectedEvidence);

        // then
        assertThat(result.enrichedFields()).hasSize(1);
        assertThat(result.enrichedDevice().getW()).isEqualTo(buildCompleteDevice().getW());
    }

    @Test
    public void populateDeviceInfoShouldEnrichPpiWhenItIsMissing() throws Exception {
        // given
        final Device testDevice = buildCompleteDevice().toBuilder()
                .ppi(null)
                .build();

        // when
        buildCompleteDeviceData();
        final CollectedEvidence collectedEvidence = CollectedEvidence.builder()
                .deviceUA("fake-UserAgent")
                .build();
        final EnrichmentResult result = target.populateDeviceInfo(testDevice, collectedEvidence);

        // then
        assertThat(result.enrichedFields()).hasSize(1);
        assertThat(result.enrichedDevice().getPpi()).isEqualTo(buildCompleteDevice().getPpi());
    }

    @Test
    public void populateDeviceInfoShouldEnrichPXRatioWhenItIsMissing() throws Exception {
        // given
        final Device testDevice = buildCompleteDevice().toBuilder()
                .pxratio(null)
                .build();

        // when
        buildCompleteDeviceData();
        final CollectedEvidence collectedEvidence = CollectedEvidence.builder()
                .deviceUA("fake-UserAgent")
                .build();
        final EnrichmentResult result = target.populateDeviceInfo(testDevice, collectedEvidence);

        // then
        assertThat(result.enrichedFields()).hasSize(1);
        assertThat(result.enrichedDevice().getPxratio()).isEqualTo(buildCompleteDevice().getPxratio());
    }

    @Test
    public void populateDeviceInfoShouldEnrichDeviceIDWhenItIsMissing() throws Exception {
        // given
        final Device testDevice = buildCompleteDevice().toBuilder()
                .ext(null)
                .build();

        // when
        buildCompleteDeviceData();
        final CollectedEvidence collectedEvidence = CollectedEvidence.builder()
                .deviceUA("fake-UserAgent")
                .build();
        final EnrichmentResult result = target.populateDeviceInfo(testDevice, collectedEvidence);

        // then
        assertThat(result.enrichedFields()).hasSize(1);
        assertThat(getDeviceId(result.enrichedDevice())).isEqualTo(getDeviceId(buildCompleteDevice()));
    }

    private static Device buildCompleteDevice() {
        final Device device = Device.builder()
                .devicetype(1)
                .make("StarFleet")
                .model("communicator")
                .os("NeutronAI")
                .osv("X-502")
                .h(5051)
                .w(3001)
                .ppi(1010)
                .pxratio(BigDecimal.valueOf(1.5))
                .ext(ExtDevice.empty())
                .build();
        device.getExt().addProperty(EXT_DEVICE_ID_KEY, new TextNode("fake-device-id"));
        return device;
    }

    private void buildCompleteDeviceData() {
        when(deviceData.getDeviceType()).thenReturn(aspectPropertyValueWith("Mobile"));
        when(deviceData.getHardwareVendor()).thenReturn(aspectPropertyValueWith("StarFleet"));
        when(deviceData.getHardwareModel()).thenReturn(aspectPropertyValueWith("communicator"));
        when(deviceData.getPlatformName()).thenReturn(aspectPropertyValueWith("NeutronAI"));
        when(deviceData.getPlatformVersion()).thenReturn(aspectPropertyValueWith("X-502"));
        when(deviceData.getScreenPixelsHeight()).thenReturn(aspectPropertyValueWith(5051));
        when(deviceData.getScreenPixelsWidth()).thenReturn(aspectPropertyValueWith(3001));
        when(deviceData.getScreenInchesHeight()).thenReturn(aspectPropertyValueWith(5.0));
        when(deviceData.getPixelRatio()).thenReturn(aspectPropertyValueWith(1.5));
        when(deviceData.getDeviceId()).thenReturn(aspectPropertyValueWith("fake-device-id"));
    }

    // MARK: - convertDeviceType

    @Test
    public void populateDeviceInfoShouldEnrichDeviceTypeWithFourWhenDeviceTypeStringIsPhone() throws Exception {
        // given
        final String typeString = "Phone";

        // when
        when(deviceData.getDeviceType()).thenReturn(aspectPropertyValueWith(typeString));
        final EnrichmentResult result = target.populateDeviceInfo(
                null,
                CollectedEvidence.builder()
                        .deviceUA("fake-UserAgent")
                        .build());
        final Integer foundValue = result.enrichedDevice().getDevicetype();

        // then
        assertThat(foundValue).isEqualTo(4);
    }

    @Test
    public void populateDeviceInfoShouldEnrichDeviceTypeWithSevenWhenDeviceTypeStringIsMediaHub() throws Exception {
        // given
        final String typeString = "MediaHub";

        // when
        when(deviceData.getDeviceType()).thenReturn(aspectPropertyValueWith(typeString));
        final EnrichmentResult result = target.populateDeviceInfo(
                null,
                CollectedEvidence.builder()
                        .deviceUA("fake-UserAgent")
                        .build());
        final Integer foundValue = result.enrichedDevice().getDevicetype();

        // then
        assertThat(foundValue).isEqualTo(7);
    }

    @Test
    public void populateDeviceInfoShouldReturnNullWhenDeviceTypeStringIsUnexpected() throws Exception {
        // given
        final String typeString = "BattleStar Atlantis";

        // when
        when(deviceData.getDeviceType()).thenReturn(aspectPropertyValueWith(typeString));
        final EnrichmentResult result = target.populateDeviceInfo(
                null,
                CollectedEvidence.builder()
                        .deviceUA("fake-UserAgent")
                        .build());

        // then
        assertThat(result).isNull();
    }

    private static <T> AspectPropertyValue<T> aspectPropertyValueWith(T value) {
        return new AspectPropertyValue<>() {
            @Override
            public boolean hasValue() {
                return true;
            }

            @Override
            public T getValue() throws NoValueException {
                return value;
            }

            @Override
            public void setValue(T t) {
                throw new UnsupportedOperationException();
            }

            @Override
            public String getNoValueMessage() {
                throw new UnsupportedOperationException();
            }

            @Override
            public void setNoValueMessage(String s) {
                throw new UnsupportedOperationException();
            }
        };
    }
}
