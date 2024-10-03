package org.prebid.server.hooks.modules.greenbids.real.time.data.v1;

import ai.onnxruntime.OnnxTensor;
import ai.onnxruntime.OnnxValue;
import ai.onnxruntime.OrtException;
import ai.onnxruntime.OrtSession;
import ai.onnxruntime.TensorInfo;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.prebid.server.exception.PreBidException;
import org.prebid.server.hooks.modules.greenbids.real.time.data.model.data.ThrottlingMessage;
import org.prebid.server.hooks.modules.greenbids.real.time.data.model.predictor.FilterService;
import org.prebid.server.hooks.modules.greenbids.real.time.data.model.predictor.OnnxModelRunner;

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

public class FilterServiceTest {

    @Mock
    private OnnxModelRunner onnxModelRunnerMock;

    @Mock
    private OrtSession.Result results;

    @Mock
    private OnnxTensor onnxTensor;

    @Mock
    private TensorInfo tensorInfo;

    private FilterService target;

    @BeforeEach
    public void setUp() {
        MockitoAnnotations.openMocks(this);
        target = new FilterService();
    }

    @Test
    public void shouldReturnFilteredBiddersWhenValidThrottlingMessagesProvided() throws OrtException, IOException {
        // given
        final List<ThrottlingMessage> throttlingMessages = createThrottlingMessages();
        final Double threshold = 0.5;
        final OnnxModelRunner onnxModelRunner = givenOnnxModelRunner();

        when(results.spliterator()).thenReturn(Arrays.asList(createOnnxItem()).spliterator());
        when(onnxTensor.getInfo()).thenReturn(tensorInfo);
        when(tensorInfo.getShape()).thenReturn(new long[]{throttlingMessages.size()});
        when(onnxTensor.getValue()).thenReturn(new float[][]{{0.2f, 0.8f}});

        // when
        final Map<String, Map<String, Boolean>> impsBiddersFilterMap = target.filterBidders(
                onnxModelRunner, throttlingMessages, threshold);

        // then
        assertThat(impsBiddersFilterMap).isNotNull();
        assertThat(impsBiddersFilterMap.get("adUnit1").get("bidder1")).isTrue();
    }

    @Test
    public void shouldThrowPreBidExceptionWhenOrtExceptionOccurs() throws OrtException {
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
    public void shouldThrowPreBidExceptionWhenThrottlingMessagesIsEmpty() {
        // given
        final List<ThrottlingMessage> throttlingMessages = Collections.emptyList();
        final Double threshold = 0.5;

        // when & then
        assertThatThrownBy(() -> target.filterBidders(onnxModelRunnerMock, throttlingMessages, threshold))
                .isInstanceOf(PreBidException.class)
                .hasMessageContaining("throttlingMessages cannot be null or empty");
    }

    @Test
    public void shouldThrowPreBidExceptionWhenTensorSizeMismatchOccurs() throws OrtException {
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
        final ThrottlingMessage throttlingMessage = ThrottlingMessage.builder()
                .browser("Chrome")
                .bidder("bidder1")
                .adUnitCode("adUnit1")
                .country("US")
                .hostname("localhost")
                .device("PC")
                .hourBucket("10")
                .minuteQuadrant("1")
                .build();
        return Collections.singletonList(throttlingMessage);
    }

    private Map.Entry<String, OnnxValue> createOnnxItem() {
        return new AbstractMap.SimpleEntry<>("probabilities", onnxTensor);
    }
}
