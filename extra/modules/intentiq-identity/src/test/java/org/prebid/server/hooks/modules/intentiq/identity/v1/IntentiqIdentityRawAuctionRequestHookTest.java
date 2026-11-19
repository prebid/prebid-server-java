package org.prebid.server.hooks.modules.intentiq.identity.v1;

import com.iab.openrtb.request.BidRequest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.prebid.server.hooks.execution.v1.auction.AuctionRequestPayloadImpl;
import org.prebid.server.hooks.modules.intentiq.identity.model.RequestPartnerContext;
import org.prebid.server.hooks.v1.InvocationAction;
import org.prebid.server.hooks.v1.InvocationResult;
import org.prebid.server.hooks.v1.InvocationStatus;
import org.prebid.server.hooks.v1.auction.AuctionInvocationContext;
import org.prebid.server.hooks.v1.auction.AuctionRequestPayload;
import org.prebid.server.json.ObjectMapperProvider;
import org.prebid.server.proto.openrtb.ext.request.ExtRequest;

import static org.assertj.core.api.Assertions.assertThat;

@ExtendWith(MockitoExtension.class)
public class IntentiqIdentityRawAuctionRequestHookTest {

    @Mock
    private AuctionInvocationContext invocationContext;

    private final IntentiqIdentityRawAuctionRequestHook target = new IntentiqIdentityRawAuctionRequestHook();

    @Test
    public void codeShouldReturnExpectedValue() {
        assertThat(target.code()).isEqualTo("intentiq-identity-raw-auction-request-hook");
    }

    @Test
    public void callShouldRemoveExtIntentiqAndKeepThePartnerIdInTheModuleContext() {
        // given
        final ExtRequest ext = ExtRequest.empty();
        ext.addProperty("intentiq", ObjectMapperProvider.mapper().createObjectNode().put("partnerId", " 777 "));
        ext.addProperty("other", ObjectMapperProvider.mapper().getNodeFactory().textNode("keep"));
        final AuctionRequestPayload payload = AuctionRequestPayloadImpl.of(BidRequest.builder().ext(ext).build());

        // when
        final InvocationResult<AuctionRequestPayload> result = target.call(payload, invocationContext).result();

        // then
        assertThat(result.status()).isEqualTo(InvocationStatus.success);
        assertThat(result.action()).isEqualTo(InvocationAction.update);
        assertThat(result.moduleContext()).isEqualTo(new RequestPartnerContext("777"));
        final BidRequest updated = result.payloadUpdate().apply(payload).bidRequest();
        assertThat(updated.getExt().containsProperty("intentiq")).isFalse();
        assertThat(updated.getExt().getProperty("other").asText()).isEqualTo("keep");
    }

    @Test
    public void callShouldRemoveAnExtIntentiqWithoutAUsablePartnerId() {
        // given
        final ExtRequest ext = ExtRequest.empty();
        ext.addProperty("intentiq", ObjectMapperProvider.mapper().getNodeFactory().textNode("777"));
        final AuctionRequestPayload payload = AuctionRequestPayloadImpl.of(BidRequest.builder().ext(ext).build());

        // when
        final InvocationResult<AuctionRequestPayload> result = target.call(payload, invocationContext).result();

        // then
        assertThat(result.moduleContext()).isEqualTo(new RequestPartnerContext(null));
        assertThat(result.payloadUpdate().apply(payload).bidRequest().getExt().containsProperty("intentiq"))
                .isFalse();
    }

    @Test
    public void callShouldDoNothingWhenTheRequestHasNoExtIntentiq() {
        // when
        final InvocationResult<AuctionRequestPayload> result = target.call(
                AuctionRequestPayloadImpl.of(BidRequest.builder().build()), invocationContext).result();

        // then
        assertThat(result.action()).isEqualTo(InvocationAction.no_action);
        assertThat(result.payloadUpdate()).isNull();
        assertThat(result.moduleContext()).isNull();
    }
}
