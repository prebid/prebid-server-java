package org.prebid.server.json;

import com.fasterxml.jackson.databind.JsonMappingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.Value;
import org.junit.Test;

import java.io.IOException;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;

public class ZonedDateTimeModuleTest {

    private static final ObjectMapper MAPPER = new ObjectMapper().registerModule(new ZonedDateTimeModule());

    @Test
    public void shouldEncodeSuccessfully() throws IOException {
        // given
        final ZonedDateTime value = ZonedDateTime.of(2017, 12, 10, 15, 45, 55, 237018349, ZoneOffset.UTC);
        final Model model = new Model(value);

        // when
        final String modelAsString = MAPPER.writeValueAsString(model);

        // then
        assertThat(modelAsString).isEqualTo("{\"value\":\"2017-12-10T15:45:55.237018349Z\"}");
    }

    @Test
    public void shouldEncodeVariableNumberOfNanos() throws IOException {
        // given
        final ZonedDateTime value = ZonedDateTime.of(2017, 12, 10, 15, 45, 55, 237018000, ZoneOffset.UTC);
        final Model model = new Model(value);

        // when
        final String modelAsString = MAPPER.writeValueAsString(model);

        // then
        assertThat(modelAsString).isEqualTo("{\"value\":\"2017-12-10T15:45:55.237018Z\"}");
    }

    @Test
    public void shouldDecodeSuccessfully() throws IOException {
        // given
        final String modelAsString = "{\"value\":\"2017-12-10T15:45:55.237018349Z\"}";

        // when
        final Model model = MAPPER.readValue(modelAsString, Model.class);

        // then
        assertThat(model.value).isEqualTo(ZonedDateTime.of(2017, 12, 10, 15, 45, 55, 237018349, ZoneOffset.UTC));
    }

    @Test
    public void shouldDecodeVariableNumberOfNanos() throws IOException {
        // given
        final String modelAsString = "{\"value\":\"2017-12-10T15:45:55.237018Z\"}";

        // when
        final Model model = MAPPER.readValue(modelAsString, Model.class);

        // then
        assertThat(model.value).isEqualTo(ZonedDateTime.of(2017, 12, 10, 15, 45, 55, 237018000, ZoneOffset.UTC));
    }

    @Test
    public void shouldThrowExceptionWhenTryingToDecode() {
        // given
        final String modelAsString = "{\"value\":\"invalid_date\"}";

        // when and then
        assertThatExceptionOfType(JsonMappingException.class)
                .isThrownBy(() -> MAPPER.readValue(modelAsString, Model.class));
    }

    @Value
    private static class Model {

        ZonedDateTime value;
    }
}
