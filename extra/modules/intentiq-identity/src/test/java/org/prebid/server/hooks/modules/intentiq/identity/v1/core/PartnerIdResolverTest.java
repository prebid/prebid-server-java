package org.prebid.server.hooks.modules.intentiq.identity.v1.core;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.iab.openrtb.request.BidRequest;
import org.junit.jupiter.api.Test;
import org.prebid.server.hooks.modules.intentiq.identity.model.config.IntentiqIdentityProperties;
import org.prebid.server.json.ObjectMapperProvider;
import org.prebid.server.proto.openrtb.ext.request.ExtRequest;
import org.prebid.server.proto.openrtb.ext.request.ExtRequestPrebid;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

public class PartnerIdResolverTest {

    private static final ObjectMapper MAPPER = ObjectMapperProvider.mapper();

    @Test
    public void resolveShouldUseTheConfiguredPartnerIdWhenTheRequestHasNone() {
        // when
        final PartnerIdResolver.Resolved resolved =
                PartnerIdResolver.resolve(properties("111", List.of("777")), BidRequest.builder().build());

        // then
        assertThat(resolved.partnerId()).isEqualTo("111");
        assertThat(resolved.requestRejected()).isFalse();
    }

    @Test
    public void resolveShouldUseTheRequestPartnerIdWhenItIsAllowed() {
        // when
        final PartnerIdResolver.Resolved resolved =
                PartnerIdResolver.resolve(properties("111", List.of("555", "777")), request(text("777")));

        // then
        assertThat(resolved.partnerId()).isEqualTo("777");
        assertThat(resolved.requestRejected()).isFalse();
    }

    @Test
    public void resolveShouldAcceptANumericRequestPartnerId() {
        // when
        final PartnerIdResolver.Resolved resolved =
                PartnerIdResolver.resolve(properties(null, List.of("777")), request(MAPPER.getNodeFactory()
                        .numberNode(777L)));

        // then
        assertThat(resolved.partnerId()).isEqualTo("777");
    }

    @Test
    public void resolveShouldTrimTheRequestAndTheListedPartnerIds() {
        // when
        final PartnerIdResolver.Resolved resolved =
                PartnerIdResolver.resolve(properties(null, List.of(" 777 ")), request(text(" 777")));

        // then
        assertThat(resolved.partnerId()).isEqualTo("777");
    }

    @Test
    public void resolveShouldFallBackToTheConfiguredPartnerIdWhenTheRequestOneIsNotAllowed() {
        // when
        final PartnerIdResolver.Resolved resolved =
                PartnerIdResolver.resolve(properties("111", List.of("777")), request(text("999")));

        // then
        assertThat(resolved.partnerId()).isEqualTo("111");
        assertThat(resolved.requestRejected()).isTrue();
    }

    @Test
    public void resolveShouldIgnoreTheRequestPartnerIdWhenNoAllowlistIsConfigured() {
        // when
        final PartnerIdResolver.Resolved resolved =
                PartnerIdResolver.resolve(properties("111", List.of()), request(text("777")));

        // then
        assertThat(resolved.partnerId()).isEqualTo("111");
        assertThat(resolved.requestRejected()).isTrue();
    }

    @Test
    public void resolveShouldReturnNoPartnerWhenTheRequestOneIsNotAllowedAndNoneIsConfigured() {
        // when
        final PartnerIdResolver.Resolved resolved =
                PartnerIdResolver.resolve(properties(null, null), request(text("777")));

        // then
        assertThat(resolved.partnerId()).isNull();
        assertThat(resolved.requestRejected()).isTrue();
    }

    @Test
    public void fromRequestShouldIgnoreBlankAndNonScalarValues() {
        assertThat(PartnerIdResolver.fromRequest(request(text("  ")))).isNull();
        assertThat(PartnerIdResolver.fromRequest(request(MAPPER.createObjectNode().put("id", "777")))).isNull();
        assertThat(PartnerIdResolver.fromRequest(request(MAPPER.getNodeFactory().booleanNode(true)))).isNull();
        assertThat(PartnerIdResolver.fromRequest(request(MAPPER.getNodeFactory().numberNode(7.5)))).isNull();
        assertThat(PartnerIdResolver.fromRequest(BidRequest.builder().build())).isNull();
    }

    @Test
    public void fromRequestShouldIgnoreAnExtIntentiqThatIsNotAnObject() {
        // given
        final ExtRequest ext = ExtRequest.empty();
        ext.addProperty("intentiq", text("777"));

        // when and then
        assertThat(PartnerIdResolver.fromRequest(BidRequest.builder().ext(ext).build())).isNull();
        assertThat(PartnerIdResolver.hasRequestField(BidRequest.builder().ext(ext).build())).isTrue();
    }

    @Test
    public void withoutRequestFieldShouldRemoveOnlyExtIntentiqAndLeaveTheOriginalUnchanged() {
        // given
        final ExtRequest ext = ExtRequest.of(ExtRequestPrebid.builder().debug(1).build());
        ext.addProperty("intentiq", MAPPER.createObjectNode().put("partnerId", "777"));
        ext.addProperty("other", text("keep"));
        final BidRequest original = BidRequest.builder().id("r1").ext(ext).build();

        // when
        final BidRequest stripped = PartnerIdResolver.withoutRequestField(original);

        // then
        assertThat(stripped.getId()).isEqualTo("r1");
        assertThat(stripped.getExt().containsProperty("intentiq")).isFalse();
        assertThat(stripped.getExt().getProperty("other")).isEqualTo(text("keep"));
        assertThat(stripped.getExt().getPrebid().getDebug()).isEqualTo(1);
        assertThat(original.getExt().containsProperty("intentiq")).isTrue();
    }

    @Test
    public void withoutRequestFieldShouldReturnTheSameRequestWhenThereIsNothingToRemove() {
        // given
        final BidRequest request = BidRequest.builder().ext(ExtRequest.empty()).build();

        // when and then
        assertThat(PartnerIdResolver.withoutRequestField(request)).isSameAs(request);
        assertThat(PartnerIdResolver.withoutRequestField(null)).isNull();
    }

    private static IntentiqIdentityProperties properties(String partnerId, List<String> allowed) {
        final IntentiqIdentityProperties properties = new IntentiqIdentityProperties();
        properties.setPartnerId(partnerId);
        properties.setAllowedPartnerIds(allowed);
        return properties;
    }

    private static BidRequest request(JsonNode partnerId) {
        final ObjectNode intentiq = MAPPER.createObjectNode();
        intentiq.set("partnerId", partnerId);
        final ExtRequest ext = ExtRequest.empty();
        ext.addProperty("intentiq", intentiq);
        return BidRequest.builder().ext(ext).build();
    }

    private static JsonNode text(String value) {
        return MAPPER.getNodeFactory().textNode(value);
    }
}
