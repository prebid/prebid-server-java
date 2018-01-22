package org.rtb.vexing.validation;

import com.fasterxml.jackson.databind.JsonNode;
import io.vertx.core.json.DecodeException;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.experimental.FieldDefaults;
import org.junit.Before;
import org.junit.Test;
import org.rtb.vexing.VertxTest;
import org.rtb.vexing.config.ApplicationConfig;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.Set;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;
import static org.assertj.core.api.Assertions.assertThatNullPointerException;

public class BidderParamValidatorTest extends VertxTest {

    private BidderParamValidator bidderParamValidator;

    @Before
    public void setUp() {
        bidderParamValidator = BidderParamValidator.create("/org/rtb/vexing/validation/schema/valid");
    }

    @Test
    public void createShouldFailOnNullArguments() {
        assertThatNullPointerException().isThrownBy(() -> BidderParamValidator.create(null));
    }

    @Test
    public void createShouldFailOnInvalidSchemaPath() {
        assertThatIllegalArgumentException().isThrownBy(() -> BidderParamValidator.create("/noschema"));
    }

    @Test
    public void createShouldFailOnEmptySchemaFile() {
        assertThatIllegalArgumentException().isThrownBy(()
                ->  BidderParamValidator.create("/org/rtb/vexing/validation/schema/emtpy"));
    }

    @Test
    public void createShouldFailOnInvalidSchemaFile() {
        assertThatIllegalArgumentException().isThrownBy(()
                ->  BidderParamValidator.create("/org/rtb/vexing/validation/schema/invalid"));
    }

    @Test
    public void validateShouldNotReturnValidationMessagesWhenBidderExtIsOk() {

        //given
        final RubiconExt ext = RubiconExt.builder().accountId(1).siteId(2).zoneId(3)
                .build();
        final JsonNode node = defaultNamingMapper.convertValue(ext, JsonNode.class);

        //when
        final Set<String> messages = bidderParamValidator
                .validate(BidderParamValidator.Bidder.rubicon, node);

        //then
        assertThat(messages).isEmpty();
    }

    @Test
    public void validateShouldReturnValidationMessagesWhenBidderExtNotValid() {

        //given
        final RubiconExt ext = RubiconExt.builder().siteId(2).zoneId(3)
                .build();

        final JsonNode node = defaultNamingMapper.convertValue(ext, JsonNode.class);

        final Set<String> messages = bidderParamValidator
                .validate(BidderParamValidator.Bidder.rubicon, node);

        //then
        assertThat(messages.size()).isEqualTo(1);
    }

    @Test
    public void schemaShouldReturnSchemasString() throws IOException {

        //given
        final String expected = readFromClasspath("/org/rtb/vexing/validation/schemas.json");
        bidderParamValidator = BidderParamValidator.create("/org/rtb/vexing/validation/schema");

        //when
        final String result = bidderParamValidator.schemas();

        //then

        assertThat(result).isEqualTo(expected);
    }

    @Test
    public void isValidShouldReturnTrueForKnownBidder() {
        assertThat(bidderParamValidator.isValidBidderName("rubicon")).isTrue();
    }

    @Test
    public void isValidShouldReturnFalseForUnknownBidder() {
        assertThat(bidderParamValidator.isValidBidderName("unknown")).isFalse();
    }

    @Builder(toBuilder = true)
    @FieldDefaults(makeFinal = true, level = AccessLevel.PUBLIC)
    private static class RubiconExt {
        Integer accountId;
        Integer siteId;
        Integer zoneId;
    }

    private static String readFromClasspath(String path) {
        String content = null;

        final InputStream resourceAsStream = ApplicationConfig.class.getResourceAsStream(path);
        if (resourceAsStream != null) {
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(resourceAsStream,
                    StandardCharsets.UTF_8))) {
                content = reader.lines().collect(Collectors.joining("\n"));
            } catch (IOException e) {

            }
        }
        return content;
    }
}