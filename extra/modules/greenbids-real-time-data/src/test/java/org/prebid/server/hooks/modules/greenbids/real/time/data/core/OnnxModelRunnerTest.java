package org.prebid.server.hooks.modules.greenbids.real.time.data.core;

import ai.onnxruntime.OnnxTensor;
import ai.onnxruntime.OrtException;
import ai.onnxruntime.OrtSession;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.Map;
import java.util.Objects;
import java.util.stream.StreamSupport;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

public class OnnxModelRunnerTest {

    private OnnxModelRunner target;

    @BeforeEach
    public void setUp() throws OrtException, IOException {
        target = givenOnnxModelRunner();
    }

    @Test
    public void runModelShouldReturnProbabilitiesWhenValidThrottlingInferenceRow() throws OrtException {
        // given
        final String[][] throttlingInferenceRow = {{
                "Chrome 59", "rubicon", "adunitcodevalue", "US", "www.leparisien.fr", "PC", "10", "1"}};

        // when
        final OrtSession.Result actualResult = target.runModel(throttlingInferenceRow);

        // then
        final float[][] probabilities = StreamSupport.stream(actualResult.spliterator(), false)
                .filter(onnxItem -> Objects.equals(onnxItem.getKey(), "probabilities"))
                .map(Map.Entry::getValue)
                .map(OnnxTensor.class::cast)
                .map(tensor -> {
                    try {
                        return (float[][]) tensor.getValue();
                    } catch (OrtException e) {
                        throw new RuntimeException(e);
                    }
                }).findFirst().get();

        assertThat(actualResult).isNotNull();
        assertThat(actualResult).hasSize(2);
        assertThat(probabilities[0]).isNotEmpty();
        assertThat(probabilities[0][0]).isBetween(0.0f, 1.0f);
        assertThat(probabilities[0][1]).isBetween(0.0f, 1.0f);
    }

    @Test
    public void runModelShouldThrowOrtExceptionWhenNonValidThrottlingInferenceRow() {
        // given
        final String[][] throttlingInferenceRowWithMissingColumn = {{
                "Chrome 59", "adunitcodevalue", "US", "www.leparisien.fr", "PC", "10", "1"}};

        // when & then
        assertThatThrownBy(() -> target.runModel(throttlingInferenceRowWithMissingColumn))
                .isInstanceOf(OrtException.class);
    }

    private OnnxModelRunner givenOnnxModelRunner() throws OrtException, IOException {
        final byte[] onnxModelBytes = Files.readAllBytes(Paths.get(
                "src/test/resources/models_pbuid=test-pbuid.onnx"));
        return new OnnxModelRunner(onnxModelBytes);
    }
}
