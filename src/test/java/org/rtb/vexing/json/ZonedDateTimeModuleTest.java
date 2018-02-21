package org.rtb.vexing.json;

import com.fasterxml.jackson.databind.JsonMappingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.Value;
import org.junit.Test;

import java.io.IOException;
import java.time.LocalDateTime;
import java.time.Month;
import java.time.ZoneId;
import java.time.ZonedDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;

public class ZonedDateTimeModuleTest {

    private static final ObjectMapper MAPPER = new ObjectMapper().registerModule(new ZonedDateTimeModule());

    @Test
    public void shouldEncodeSuccessfully() throws IOException {
        // given
        final ZonedDateTime VALUE = ZonedDateTime.of(LocalDateTime.of(2017, Month.DECEMBER, 10, 15, 45, 55, 237018349),
                ZoneId.of("UTC"));
        final Model model = new Model(VALUE);

        // when
        final String modelAsString = MAPPER.writeValueAsString(model);

        // then
        assertThat(modelAsString).isEqualTo("{\"value\":\"2017-12-10T15:45:55.237018349Z\"}");
    }

    @Test
    public void shouldDecodeSuccessfully() throws IOException {
        // given
        final String modelAsString = "{\"value\":\"2017-12-10T15:45:55.237018349Z\"}";

        // when
        final Model model = MAPPER.readValue(modelAsString, Model.class);

        // then
        assertThat(model.value.getYear()).isEqualTo(2017);
        assertThat(model.value.getMonth()).isEqualTo(Month.DECEMBER);
        assertThat(model.value.getDayOfMonth()).isEqualTo(10);
        assertThat(model.value.getHour()).isEqualTo(15);
        assertThat(model.value.getMinute()).isEqualTo(45);
        assertThat(model.value.getSecond()).isEqualTo(55);
        assertThat(model.value.getNano()).isEqualTo(237018349);
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
