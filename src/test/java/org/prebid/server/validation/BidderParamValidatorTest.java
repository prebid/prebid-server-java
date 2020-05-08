package org.prebid.server.validation;

import com.fasterxml.jackson.databind.JsonNode;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnit;
import org.mockito.junit.MockitoRule;
import org.prebid.server.VertxTest;
import org.prebid.server.bidder.BidderCatalog;
import org.prebid.server.proto.openrtb.ext.request.adform.ExtImpAdform;
import org.prebid.server.proto.openrtb.ext.request.adtelligent.ExtImpAdtelligent;
import org.prebid.server.proto.openrtb.ext.request.appnexus.ExtImpAppnexus;
import org.prebid.server.proto.openrtb.ext.request.beachfront.ExtImpBeachfront;
import org.prebid.server.proto.openrtb.ext.request.brightroll.ExtImpBrightroll;
import org.prebid.server.proto.openrtb.ext.request.eplanning.ExtImpEplanning;
import org.prebid.server.proto.openrtb.ext.request.facebook.ExtImpFacebook;
import org.prebid.server.proto.openrtb.ext.request.openx.ExtImpOpenx;
import org.prebid.server.proto.openrtb.ext.request.rubicon.ExtImpRubicon;
import org.prebid.server.proto.openrtb.ext.request.somoaudience.ExtImpSomoaudience;
import org.prebid.server.proto.openrtb.ext.request.sovrn.ExtImpSovrn;
import org.prebid.server.util.ResourceUtil;

import java.io.IOException;
import java.math.BigDecimal;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

import static java.util.Arrays.asList;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;
import static org.mockito.BDDMockito.given;

public class BidderParamValidatorTest extends VertxTest {

    private static final String RUBICON = "rubicon";
    private static final String APPNEXUS = "appnexus";
    private static final String ADFORM = "adform";
    private static final String BRIGHTROLL = "brightroll";
    private static final String SOVRN = "sovrn";
    private static final String ADTELLIGENT = "adtelligent";
    private static final String FACEBOOK = "audienceNetwork";
    private static final String OPENX = "openx";
    private static final String EPLANNING = "eplanning";
    private static final String SOMOAUDIENCE = "somoaudience";
    private static final String BEACHFRONT = "beachfront";

    @Rule
    public final MockitoRule mockitoRule = MockitoJUnit.rule();

    @Mock
    private BidderCatalog bidderCatalog;

    private BidderParamValidator bidderParamValidator;

    @Before
    public void setUp() {
        given(bidderCatalog.names()).willReturn(new HashSet<>(
                asList(RUBICON, APPNEXUS, ADFORM, BRIGHTROLL, SOVRN, ADTELLIGENT, FACEBOOK, OPENX, EPLANNING,
                        SOMOAUDIENCE, BEACHFRONT)));

        bidderParamValidator = BidderParamValidator.create(bidderCatalog, "static/bidder-params", jacksonMapper);
    }

    @Test
    public void createShouldFailOnInvalidSchemaPath() {
        assertThatIllegalArgumentException().isThrownBy(
                () -> BidderParamValidator.create(bidderCatalog, "noschema", jacksonMapper));
    }

    @Test
    public void createShouldFailOnEmptySchemaFile() {
        assertThatIllegalArgumentException().isThrownBy(
                () -> BidderParamValidator.create(
                        bidderCatalog, "org/prebid/server/validation/schema/empty", jacksonMapper));
    }

    @Test
    public void createShouldFailOnInvalidSchemaFile() {
        assertThatIllegalArgumentException().isThrownBy(
                () -> BidderParamValidator.create(
                        bidderCatalog, "org/prebid/server/validation/schema/invalid", jacksonMapper));
    }

    @Test
    public void validateShouldNotReturnValidationMessagesWhenRubiconImpExtIsOk() {
        // given
        final ExtImpRubicon ext = ExtImpRubicon.builder().accountId(1).siteId(2).zoneId(3).build();
        final JsonNode node = mapper.convertValue(ext, JsonNode.class);

        // when
        final Set<String> messages = bidderParamValidator.validate(RUBICON, node);

        // then
        assertThat(messages).isEmpty();
    }

    @Test
    public void validateShouldReturnValidationMessagesWhenRubiconImpExtNotValid() {
        // given
        final ExtImpRubicon ext = ExtImpRubicon.builder().siteId(2).zoneId(3).build();

        final JsonNode node = mapper.convertValue(ext, JsonNode.class);

        final Set<String> messages = bidderParamValidator.validate(RUBICON, node);

        // then
        assertThat(messages.size()).isEqualTo(1);
    }

    @Test
    public void validateShouldReturnValidationMessagesWhenAppnexusImpExtNotValid() {
        // given
        final ExtImpAppnexus ext = ExtImpAppnexus.builder().member("memberId").build();

        final JsonNode node = mapper.convertValue(ext, JsonNode.class);

        // when
        final Set<String> messages = bidderParamValidator.validate(APPNEXUS, node);

        // then
        assertThat(messages.size()).isEqualTo(4);
    }

    @Test
    public void validateShouldNotReturnValidationMessagesWhenAppnexusImpExtIsOk() {
        // given
        final ExtImpAppnexus ext = ExtImpAppnexus.builder().placementId(1).build();

        final JsonNode node = mapper.convertValue(ext, JsonNode.class);

        // when
        final Set<String> messages = bidderParamValidator.validate(APPNEXUS, node);

        // then
        assertThat(messages).isEmpty();
    }

    @Test
    public void validateShouldNotReturnValidationMessagesWhenAdformImpExtIsOk() {
        // given
        final ExtImpAdform ext = ExtImpAdform.of(15L, "gross", null, null);

        final JsonNode node = mapper.convertValue(ext, JsonNode.class);

        // when
        final Set<String> messages = bidderParamValidator.validate(ADFORM, node);

        // then
        assertThat(messages).isEmpty();
    }

    @Test
    public void validateShouldReturnValidationMessagesWhenAdformImpExtNotValid() {
        // given
        final JsonNode node = mapper.createObjectNode();

        // when
        final Set<String> messages = bidderParamValidator.validate(ADFORM, node);

        // then
        assertThat(messages.size()).isEqualTo(1);
    }

    @Test
    public void validateShouldNotReturnValidationMessagesWhenBrightrollImpExtIsOk() {
        // given
        final ExtImpBrightroll ext = ExtImpBrightroll.of("publisher");

        final JsonNode node = mapper.convertValue(ext, JsonNode.class);

        // when
        final Set<String> messages = bidderParamValidator.validate(BRIGHTROLL, node);

        // then
        assertThat(messages).isEmpty();
    }

    @Test
    public void validateShouldReturnValidationMessagesWhenBrightrollExtNotValid() {
        // given
        final JsonNode node = mapper.createObjectNode();

        // when
        final Set<String> messages = bidderParamValidator.validate(BRIGHTROLL, node);

        // then
        assertThat(messages.size()).isEqualTo(1);
    }

    @Test
    public void validateShouldNotReturnValidationMessagesWhenSovrnImpExtIsOk() {
        // given
        final ExtImpSovrn ext = ExtImpSovrn.of("tag", null, null);

        final JsonNode node = mapper.convertValue(ext, JsonNode.class);

        // when
        final Set<String> messages = bidderParamValidator.validate(SOVRN, node);

        // then
        assertThat(messages).isEmpty();
    }

    @Test
    public void validateShouldReturnValidationMessagesWhenSovrnExtNotValid() {
        // given
        final JsonNode node = mapper.createObjectNode();

        // when
        final Set<String> messages = bidderParamValidator.validate(SOVRN, node);

        // then
        assertThat(messages.size()).isEqualTo(2);
    }

    @Test
    public void validateShouldNotReturnValidationMessagesWhenAdtelligentImpExtIsOk() {
        // given
        final ExtImpAdtelligent ext = ExtImpAdtelligent.of(15, 1, 2, BigDecimal.valueOf(3));

        final JsonNode node = mapper.convertValue(ext, JsonNode.class);

        // when
        final Set<String> messages = bidderParamValidator.validate(ADTELLIGENT, node);

        // then
        assertThat(messages).isEmpty();
    }

    @Test
    public void validateShouldReturnValidationMessagesWhenAdtelligentImpExtNotValid() {
        // given
        final JsonNode node = mapper.createObjectNode();

        // when
        final Set<String> messages = bidderParamValidator.validate(ADTELLIGENT, node);

        // then
        assertThat(messages.size()).isEqualTo(1);
    }

    @Test
    public void validateShouldNotReturnValidationMessagesWhenFacebookImpExtIsOk() {
        // given
        final ExtImpFacebook ext = ExtImpFacebook.of("placementId", "publisherId");

        final JsonNode node = mapper.convertValue(ext, JsonNode.class);

        // when
        final Set<String> messages = bidderParamValidator.validate(FACEBOOK, node);

        // then
        assertThat(messages).isEmpty();
    }

    @Test
    public void validateShouldReturnValidationMessagesWhenFacebookExtNotValid() {
        // given
        final JsonNode node = mapper.createObjectNode();

        // when
        final Set<String> messages = bidderParamValidator.validate(FACEBOOK, node);

        // then
        assertThat(messages.size()).isEqualTo(1);
    }

    @Test
    public void validateShouldNotReturnValidationMessagesWhenOpenxImpExtIsOk() {
        // given
        final ExtImpOpenx ext = ExtImpOpenx.builder()
                .customParams(Collections.singletonMap("foo", mapper.convertValue("bar", JsonNode.class)))
                .customFloor(BigDecimal.valueOf(0.2))
                .delDomain("se-demo-d.openx.net")
                .unit("2222")
                .build();
        final JsonNode node = mapper.convertValue(ext, JsonNode.class);

        // when
        final Set<String> messages = bidderParamValidator.validate(OPENX, node);

        // then
        assertThat(messages).isEmpty();
    }

    @Test
    public void validateShouldReturnValidationMessagesWhenOpenxExtNotValid() {
        // given
        final ExtImpOpenx ext = ExtImpOpenx.builder()
                .customParams(Collections.singletonMap("foo", mapper.convertValue("bar", JsonNode.class)))
                .customFloor(BigDecimal.valueOf(0.2))
                .delDomain("se-demo-d.openx.net")
                .unit("not-numeric")
                .build();
        final JsonNode node = mapper.convertValue(ext, JsonNode.class);

        // when
        final Set<String> messages = bidderParamValidator.validate(OPENX, node);

        // then
        assertThat(messages.size()).isEqualTo(1);
    }

    @Test
    public void validateShouldNotReturnValidationMessagesWhenEplanningImpExtIsOk() {
        // given
        final ExtImpEplanning ext = ExtImpEplanning.of("clientId", "");
        final JsonNode node = mapper.convertValue(ext, JsonNode.class);

        // when
        final Set<String> messages = bidderParamValidator.validate(EPLANNING, node);

        // then
        assertThat(messages).isEmpty();
    }

    @Test
    public void validateShouldReturnValidationMessagesWhenEplanningExtNotValid() {
        // given
        final JsonNode node = mapper.createObjectNode().put("exchange_id", 5);

        // when
        final Set<String> messages = bidderParamValidator.validate(EPLANNING, node);

        // then
        assertThat(messages.size()).isEqualTo(1);
    }

    @Test
    public void validateShouldNotReturnValidationMessagesWhenSomoaudienceImpExtIsOk() {
        // given
        final ExtImpSomoaudience ext = ExtImpSomoaudience.of("placementId", BigDecimal.valueOf(1.11));
        final JsonNode node = mapper.convertValue(ext, JsonNode.class);

        // when
        final Set<String> messages = bidderParamValidator.validate(SOMOAUDIENCE, node);

        // then
        assertThat(messages).isEmpty();
    }

    @Test
    public void validateShouldReturnValidationMessagesWhenSomoaudienceExtNotValid() {
        // given
        final JsonNode node = mapper.createObjectNode();

        // when
        final Set<String> messages = bidderParamValidator.validate(SOMOAUDIENCE, node);

        // then
        assertThat(messages.size()).isEqualTo(1);
    }

    @Test
    public void validateShouldNotReturnValidationMessagesWhenBeachfrontImpExtIsOk() {
        // given
        final ExtImpBeachfront ext = ExtImpBeachfront.of("appId", null, BigDecimal.ONE, "adm");
        final JsonNode node = mapper.convertValue(ext, JsonNode.class);

        // when
        final Set<String> messages = bidderParamValidator.validate(BEACHFRONT, node);

        // then
        assertThat(messages).isEmpty();
    }

    @Test
    public void validateShouldReturnValidationMessagesWhenBeachfrontExtNotValid() {
        // given
        final JsonNode node = mapper.createObjectNode();

        // when
        final Set<String> messages = bidderParamValidator.validate(BEACHFRONT, node);

        // then
        assertThat(messages.size()).isEqualTo(3);
    }

    @Test
    public void schemaShouldReturnSchemasString() throws IOException {
        // given
        given(bidderCatalog.names()).willReturn(new HashSet<>(asList("test-rubicon", "test-appnexus")));

        bidderParamValidator = BidderParamValidator.create(
                bidderCatalog, "org/prebid/server/validation/schema/valid", jacksonMapper);

        // when
        final String result = bidderParamValidator.schemas();

        // then
        assertThat(result).isEqualTo(ResourceUtil.readFromClasspath(
                "org/prebid/server/validation/schema//valid/test-schemas.json"));
    }
}
