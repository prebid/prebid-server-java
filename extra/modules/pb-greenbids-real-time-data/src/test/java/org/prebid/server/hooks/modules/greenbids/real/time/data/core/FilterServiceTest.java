package org.prebid.server.hooks.modules.greenbids.real.time.data.core;

import ai.onnxruntime.OnnxTensor;
import ai.onnxruntime.OnnxValue;
import ai.onnxruntime.OrtException;
import ai.onnxruntime.OrtSession;
import ai.onnxruntime.TensorInfo;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.prebid.server.exception.PreBidException;
import org.prebid.server.hooks.modules.greenbids.real.time.data.model.data.ThrottlingMessage;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.AbstractMap;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
public class FilterServiceTest {

    @Mock
    private OnnxModelRunner onnxModelRunnerMock;

    @Mock
    private OrtSession.Result results;

    @Mock
    private OnnxTensor onnxTensor;

    @Mock
    private TensorInfo tensorInfo;

    @Mock
    private OnnxValue onnxValue;

    private final FilterService target = new FilterService();

    @Test
    public void filterBiddersShouldReturnFilteredBiddersWhenValidThrottlingMessagesProvided()
            throws OrtException, IOException {
        // given
        final List<ThrottlingMessage> throttlingMessages = createThrottlingMessages();
        final Double threshold = 0.5;
        final OnnxModelRunner onnxModelRunner = givenOnnxModelRunner();

        // when
        final Map<String, Map<String, Boolean>> impsBiddersFilterMap = target.filterBidders(
                onnxModelRunner, throttlingMessages, threshold);

        // then
        assertThat(impsBiddersFilterMap).isNotNull();
        assertThat(impsBiddersFilterMap.get("adUnit1").get("bidder1")).isTrue();
        assertThat(impsBiddersFilterMap.get("adUnit2").get("bidder2")).isFalse();
        assertThat(impsBiddersFilterMap.get("adUnit3").get("bidder3")).isFalse();
    }

    @Test
    public void validateOnnxTensorShouldThrowPreBidExceptionWhenOnnxValueIsNotTensor() throws OrtException {
        // given
        final List<ThrottlingMessage> throttlingMessages = createThrottlingMessages();
        final Double threshold = 0.5;

        when(onnxModelRunnerMock.runModel(any(String[][].class))).thenReturn(results);
        when(results.spliterator()).thenReturn(Arrays.asList(createInvalidOnnxItem()).spliterator());

        // when & then
        assertThatThrownBy(() -> target.filterBidders(onnxModelRunnerMock, throttlingMessages, threshold))
                .isInstanceOf(PreBidException.class)
                .hasMessageContaining("Expected OnnxTensor for 'probabilities', but found");
    }

    @Test
    public void filterBiddersShouldThrowPreBidExceptionWhenOrtExceptionOccurs() throws OrtException {
        // given
        final List<ThrottlingMessage> throttlingMessages = createThrottlingMessages();
        final Double threshold = 0.5;

        when(onnxModelRunnerMock.runModel(any(String[][].class)))
                .thenThrow(new OrtException("Exception during runModel"));

        // when & then
        assertThatThrownBy(() -> target.filterBidders(onnxModelRunnerMock, throttlingMessages, threshold))
                .isInstanceOf(PreBidException.class)
                .hasMessageContaining("Exception during model inference");
    }

    @Test
    public void filterBiddersShouldThrowPreBidExceptionWhenThrottlingMessagesIsEmpty() {
        // given
        final List<ThrottlingMessage> throttlingMessages = Collections.emptyList();
        final Double threshold = 0.5;

        // when & then
        assertThatThrownBy(() -> target.filterBidders(onnxModelRunnerMock, throttlingMessages, threshold))
                .isInstanceOf(PreBidException.class)
                .hasMessageContaining("throttlingMessages cannot be null or empty");
    }

    @Test
    public void filterBiddersShouldThrowPreBidExceptionWhenTensorSizeMismatchOccurs() throws OrtException {
        // given
        final List<ThrottlingMessage> throttlingMessages = createThrottlingMessages();
        final Double threshold = 0.5;

        when(onnxModelRunnerMock.runModel(any(String[][].class))).thenReturn(results);
        when(results.spliterator()).thenReturn(Arrays.asList(createOnnxItem()).spliterator());
        when(onnxTensor.getInfo()).thenReturn(tensorInfo);
        when(tensorInfo.getShape()).thenReturn(new long[]{0});

        // when & then
        assertThatThrownBy(() -> target.filterBidders(onnxModelRunnerMock, throttlingMessages, threshold))
                .isInstanceOf(PreBidException.class)
                .hasMessageContaining("Mismatch between tensor size and throttlingMessages size");
    }

    private OnnxModelRunner givenOnnxModelRunner() throws OrtException, IOException {
        final byte[] onnxModelBytes = Files.readAllBytes(Paths.get(
                "src/test/resources/models_pbuid=test-pbuid.onnx"));
        return new OnnxModelRunner(onnxModelBytes);
    }

    private List<ThrottlingMessage> createThrottlingMessages() {
        final ThrottlingMessage throttlingMessage1 = ThrottlingMessage.builder()
                .browser("Chrome")
                .bidder("bidder1")
                .adUnitCode("adUnit1")
                .country("US")
                .hostname("localhost")
                .device("PC")
                .hourBucket("10")
                .minuteQuadrant("1")
                .build();

        final ThrottlingMessage throttlingMessage2 = ThrottlingMessage.builder()
                .browser("Firefox")
                .bidder("bidder2")
                .adUnitCode("adUnit2")
                .country("FR")
                .hostname("www.leparisien.fr")
                .device("Mobile")
                .hourBucket("11")
                .minuteQuadrant("2")
                .build();

        final ThrottlingMessage throttlingMessage3 = ThrottlingMessage.builder()
                .browser("Safari")
                .bidder("bidder3")
                .adUnitCode("adUnit3")
                .country("FR")
                .hostname("www.lesechos.fr")
                .device("Tablet")
                .hourBucket("12")
                .minuteQuadrant("3")
                .build();

        return Arrays.asList(throttlingMessage1, throttlingMessage2, throttlingMessage3);
    }

    private Map.Entry<String, OnnxValue> createOnnxItem() {
        return new AbstractMap.SimpleEntry<>("probabilities", onnxTensor);
    }

    private Map.Entry<String, OnnxValue> createInvalidOnnxItem() {
        return new AbstractMap.SimpleEntry<>("probabilities", onnxValue);
    }
}
