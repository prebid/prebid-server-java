package org.rtb.vexing.validation;

import com.fasterxml.jackson.databind.JsonNode;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnit;
import org.mockito.junit.MockitoRule;
import org.rtb.vexing.VertxTest;
import org.rtb.vexing.auction.BidderRequesterCatalog;
import org.rtb.vexing.model.openrtb.ext.request.appnexus.ExtImpAppnexus;
import org.rtb.vexing.model.openrtb.ext.request.rubicon.ExtImpRubicon;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.HashSet;
import java.util.Set;
import java.util.stream.Collectors;

import static java.util.Arrays.asList;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.BDDMockito.given;

public class BidderParamValidatorTest extends VertxTest {

    private static final String RUBICON = "rubicon";
    private static final String APPNEXUS = "appnexus";

    @Rule
    public final MockitoRule mockitoRule = MockitoJUnit.rule();

    @Mock
    private BidderRequesterCatalog bidderRequesterCatalog;

    private BidderParamValidator bidderParamValidator;

    @Before
    public void setUp() {
        given(bidderRequesterCatalog.names()).willReturn(new HashSet<>(asList(RUBICON, APPNEXUS)));

        bidderParamValidator = BidderParamValidator.create(bidderRequesterCatalog, "/static/bidder-params");
    }

    @Test
    public void createShouldFailOnNullArguments() {
        assertThatNullPointerException().isThrownBy(() -> BidderParamValidator.create(null, null));
        assertThatNullPointerException().isThrownBy(() -> BidderParamValidator.create(bidderRequesterCatalog, null));
    }

    @Test
    public void createShouldFailOnInvalidSchemaPath() {
        assertThatIllegalArgumentException().isThrownBy(
                () -> BidderParamValidator.create(bidderRequesterCatalog, "/noschema"));
    }

    @Test
    public void createShouldFailOnEmptySchemaFile() {
        assertThatIllegalArgumentException().isThrownBy(
                () -> BidderParamValidator.create(bidderRequesterCatalog, "schema/empty"));
    }

    @Test
    public void createShouldFailOnInvalidSchemaFile() {
        assertThatIllegalArgumentException().isThrownBy(
                () -> BidderParamValidator.create(bidderRequesterCatalog, "schema/invalid"));
    }

    @Test
    public void validateShouldNotReturnValidationMessagesWhenRubiconImpExtIsOk() {

        // given
        final ExtImpRubicon ext = ExtImpRubicon.builder().accountId(1).siteId(2).zoneId(3).build();
        final JsonNode node = mapper.convertValue(ext, JsonNode.class);

        // when
        final Set<String> messages = bidderParamValidator.validate(RUBICON, node);

        //then
        assertThat(messages).isEmpty();
    }

    @Test
    public void validateShouldReturnValidationMessagesWhenRubiconImpExtNotValid() {

        // given
        final ExtImpRubicon ext = ExtImpRubicon.builder().siteId(2).zoneId(3).build();

        final JsonNode node = mapper.convertValue(ext, JsonNode.class);

        final Set<String> messages = bidderParamValidator.validate(RUBICON, node);

        //then
        assertThat(messages.size()).isEqualTo(1);
    }

    @Test
    public void validateShouldReturnValidationMessagesWhenAppnexusImpExtNotValid() {

        // given
        final ExtImpAppnexus ext = ExtImpAppnexus.builder().member("memberId").build();

        final JsonNode node = mapper.convertValue(ext, JsonNode.class);

        // when
        final Set<String> messages = bidderParamValidator.validate(APPNEXUS, node);

        //then
        assertThat(messages.size()).isEqualTo(2);
    }

    @Test
    public void validateShouldNotReturnValidationMessagesWhenAppnexusImpExtExtIsOk() {

        // given
        final ExtImpAppnexus ext = ExtImpAppnexus.builder().placementId(1).build();

        final JsonNode node = mapper.convertValue(ext, JsonNode.class);

        // when
        final Set<String> messages = bidderParamValidator.validate(APPNEXUS, node);

        //then
        assertThat(messages).isEmpty();
    }

    @Test
    public void schemaShouldReturnSchemasString() throws IOException {

        //given
        given(bidderRequesterCatalog.names()).willReturn(new HashSet<>(asList("test-rubicon", "test-appnexus")));

        bidderParamValidator = BidderParamValidator.create(bidderRequesterCatalog, "schema/valid");

        // when
        final String result = bidderParamValidator.schemas();

        //then
        assertThat(result).isEqualTo(readFromClasspath("schema/valid/test-schemas.json"));
    }

    private static String readFromClasspath(String path) throws IOException {
        String content = null;

        final InputStream resourceAsStream = BidderParamValidatorTest.class.getResourceAsStream(path);
        if (resourceAsStream != null) {
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(resourceAsStream,
                    StandardCharsets.UTF_8))) {
                content = reader.lines().collect(Collectors.joining("\n"));
            }
        }
        return content;
    }
}