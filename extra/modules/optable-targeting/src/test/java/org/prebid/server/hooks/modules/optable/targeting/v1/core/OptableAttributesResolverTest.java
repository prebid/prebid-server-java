package org.prebid.server.hooks.modules.optable.targeting.v1.core;

import com.fasterxml.jackson.databind.node.ObjectNode;
import com.fasterxml.jackson.databind.node.TextNode;
import com.iab.gpp.encoder.GppModel;
import com.iab.openrtb.request.BidRequest;
import com.iab.openrtb.request.Device;
import com.iab.openrtb.request.Regs;
import com.iab.openrtb.request.User;
import org.apache.commons.lang3.StringUtils;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.prebid.server.activity.Activity;
import org.prebid.server.activity.infrastructure.ActivityInfrastructure;
import org.prebid.server.auction.IpAddressHelper;
import org.prebid.server.auction.gpp.model.GppContext;
import org.prebid.server.auction.model.AuctionContext;
import org.prebid.server.hooks.modules.optable.targeting.model.App;
import org.prebid.server.hooks.modules.optable.targeting.model.OptableAttributes;
import org.prebid.server.hooks.modules.optable.targeting.model.config.OptableTargetingProperties;
import org.prebid.server.hooks.modules.optable.targeting.v1.BaseOptableTest;
import org.prebid.server.privacy.gdpr.model.TcfContext;
import org.prebid.server.privacy.model.Privacy;
import org.prebid.server.privacy.model.PrivacyContext;
import org.prebid.server.proto.openrtb.ext.request.ExtRegs;
import org.prebid.server.proto.openrtb.ext.request.ExtUser;

import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mock.Strictness.LENIENT;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
public class OptableAttributesResolverTest extends BaseOptableTest {

    private static final String X_FORWARDED_FOR_HEADER = "X-Forwarded-For";

    @Mock(strictness = LENIENT)
    private TcfContext tcfContext;

    @Mock(strictness = LENIENT)
    private GppContext gppContext;

    @Mock(strictness = LENIENT)
    private ActivityInfrastructure activityInfrastructure;

    @Mock(strictness = LENIENT)
    private IpAddressHelper ipAddressHelper;

    @Mock
    private OptableTargetingProperties properties;

    @BeforeEach
    public void setUp() {
        when(properties.getTimeout()).thenReturn(100L);
        when(activityInfrastructure.isAllowed(any(), any())).thenReturn(true);
    }

    @Test
    public void shouldResolveGdprAttributesForORTB26WhenConsentIsValid() {
        // given
        final GppModel gppModel = mock();
        when(gppModel.encode()).thenReturn("consent");
        when(gppContext.scope()).thenReturn(GppContext.Scope.of(gppModel, Set.of(1)));
        final BidRequest bidRequest = givenBidRequestWithGdprORTB26(true, "consent");
        final AuctionContext auctionContext = givenAuctionContext(bidRequest, tcfContext, gppContext);

        // when
        final OptableAttributes result = OptableAttributesResolver.resolveAttributes(
                auctionContext, bidRequest, properties.getTimeout(), 0.01, ipAddressHelper);

        // then
        assertThat(result).isNotNull()
                .returns(true, OptableAttributes::isGdprApplies)
                .returns("consent", OptableAttributes::getGdprConsent);
    }

    @Test
    public void shouldResolveGdprAttributesForORTB25WhenConsentIsValid() {
        // given
        final GppModel gppModel = mock();
        when(gppModel.encode()).thenReturn("consent");
        when(gppContext.scope()).thenReturn(GppContext.Scope.of(gppModel, Set.of(1)));
        final BidRequest bidRequest = givenBidRequestWithGdprORTB25(true, "consent");
        final AuctionContext auctionContext = givenAuctionContext(bidRequest, tcfContext, gppContext);

        // when
        final OptableAttributes result = OptableAttributesResolver.resolveAttributes(
                auctionContext, bidRequest, properties.getTimeout(), 0.01, ipAddressHelper);

        // then
        assertThat(result).isNotNull()
                .returns(true, OptableAttributes::isGdprApplies)
                .returns("consent", OptableAttributes::getGdprConsent);
    }

    @Test
    public void shouldNotResolveTcfAttributesWhenConsentIsNotValid() {
        // given
        final GppModel gppModel = mock();
        when(tcfContext.isConsentValid()).thenReturn(false);
        when(tcfContext.getConsentString()).thenReturn("consent");
        when(gppModel.encode()).thenReturn("consent");
        when(gppContext.scope()).thenReturn(GppContext.Scope.of(gppModel, Set.of(1)));
        final BidRequest bidRequest = givenBidRequest();
        final AuctionContext auctionContext = givenAuctionContext(bidRequest, tcfContext, gppContext);

        // when
        final OptableAttributes result = OptableAttributesResolver.resolveAttributes(
                auctionContext, bidRequest, properties.getTimeout(), 0.01, ipAddressHelper);

        // then
        assertThat(result).isNotNull()
                .returns(false, OptableAttributes::isGdprApplies)
                .returns(null, OptableAttributes::getGdprConsent);
    }

    @Test
    public void shouldResolveGppAttributes() {
        // given
        final GppModel gppModel = mock();
        when(tcfContext.isConsentValid()).thenReturn(false);
        when(tcfContext.getConsentString()).thenReturn("consent");
        when(gppModel.encode()).thenReturn("consent");
        when(gppContext.scope()).thenReturn(GppContext.Scope.of(gppModel, Set.of(1)));
        final BidRequest bidRequest = givenBidRequest();
        final AuctionContext auctionContext = givenAuctionContext(bidRequest, tcfContext, gppContext);

        // when
        final OptableAttributes result = OptableAttributesResolver.resolveAttributes(
                auctionContext, bidRequest, properties.getTimeout(), 0.01, ipAddressHelper);

        // then
        assertThat(result).isNotNull()
                .returns(false, OptableAttributes::isGdprApplies)
                .returns("consent", OptableAttributes::getGpp)
                .returns(Set.of(1), OptableAttributes::getGppSid);
    }

    @Test
    public void shouldResolveAppWhenAppIsPresent() {
        // given
        final com.iab.openrtb.request.App ortbApp = com.iab.openrtb.request.App.builder()
                .bundle("com.example.app")
                .ver("1.2.3")
                .build();
        final BidRequest bidRequest = BidRequest.builder()
                .app(ortbApp)
                .build();
        final AuctionContext auctionContext = givenAuctionContext(bidRequest, tcfContext, gppContext);

        // when
        final OptableAttributes result = OptableAttributesResolver.resolveAttributes(
                auctionContext, bidRequest, properties.getTimeout(), 0.01, ipAddressHelper);

        // then
        assertThat(result).isNotNull()
                .returns(App.of("com.example.app", "1.2.3"), OptableAttributes::getApp);
    }

    @Test
    public void shouldNotResolveAppWhenAppIsAbsent() {
        // given
        final BidRequest bidRequest = BidRequest.builder().build();
        final AuctionContext auctionContext = givenAuctionContext(bidRequest, tcfContext, gppContext);

        // when
        final OptableAttributes result = OptableAttributesResolver.resolveAttributes(
                auctionContext, bidRequest, properties.getTimeout(), 0.01, ipAddressHelper);

        // then
        assertThat(result).isNotNull()
                .returns(null, OptableAttributes::getApp);
    }

    @Test
    public void shouldResolveId5SignatureWhenPresentInUserExtOptable() {
        // given
        final BidRequest bidRequest = givenBidRequestWithId5Signature("signature");
        final AuctionContext auctionContext = givenAuctionContext(bidRequest, tcfContext, gppContext);

        // when
        final OptableAttributes result = OptableAttributesResolver.resolveAttributes(
                auctionContext, bidRequest, properties.getTimeout(), 0.01, ipAddressHelper);

        // then
        assertThat(result).isNotNull()
                .returns("signature", OptableAttributes::getId5Signature);
    }

    @Test
    public void shouldNotResolveId5SignatureWhenAbsentInUserExtOptable() {
        // given
        final BidRequest bidRequest = givenBidRequestWithId5Signature(null);
        final AuctionContext auctionContext = givenAuctionContext(bidRequest, tcfContext, gppContext);

        // when
        final OptableAttributes result = OptableAttributesResolver.resolveAttributes(
                auctionContext, bidRequest, properties.getTimeout(), 0.01, ipAddressHelper);

        // then
        assertThat(result).isNotNull()
                .returns(null, OptableAttributes::getId5Signature);
    }

    @Test
    public void shouldResolveIpsFromDeviceWhenDeviceIpsArePresent() {
        // given
        final BidRequest bidRequest = BidRequest.builder()
                .device(Device.builder().ip("1.2.3.4").ipv6("2001:db8::1").build())
                .build();
        final AuctionContext auctionContext =
                givenAuctionContext(bidRequest, tcfContext, gppContext);

        // when
        final OptableAttributes result = OptableAttributesResolver.resolveAttributes(
                auctionContext, bidRequest, properties.getTimeout(), 0.01, ipAddressHelper);

        // then
        assertThat(result).isNotNull()
                .returns(List.of("1.2.3.4", "2001:db8::1"), OptableAttributes::getIps);
    }

    @Test
    public void shouldResolveIpFromXForwardedForHeaderWhenDeviceIpsAreAbsent() {
        // given
        final BidRequest bidRequest = givenBidRequest();
        final AuctionContext auctionContext =
                givenAuctionContextWithHeaders(bidRequest, Map.of(X_FORWARDED_FOR_HEADER, "1.2.3.4"));

        // when
        final OptableAttributes result = OptableAttributesResolver.resolveAttributes(
                auctionContext, bidRequest, properties.getTimeout(), 0.01, ipAddressHelper);

        // then
        assertThat(result).isNotNull()
                .returns(List.of("1.2.3.4"), OptableAttributes::getIps);
    }

    @Test
    public void shouldResolveFirstIpFromXForwardedForHeaderWhenItContainsMultipleIps() {
        // given
        final BidRequest bidRequest = givenBidRequest();
        final AuctionContext auctionContext =
                givenAuctionContextWithHeaders(bidRequest, Map.of(X_FORWARDED_FOR_HEADER, "1.2.3.4, 5.6.7.8"));

        // when
        final OptableAttributes result = OptableAttributesResolver.resolveAttributes(
                auctionContext, bidRequest, properties.getTimeout(), 0.01, ipAddressHelper);

        // then
        assertThat(result).isNotNull()
                .returns(List.of("1.2.3.4"), OptableAttributes::getIps);
    }

    @Test
    public void shouldNotMaskIpFromXForwardedForHeaderWhenTransmitUfpdIsAllowed() {
        // given
        final BidRequest bidRequest = givenBidRequest();
        final AuctionContext auctionContext =
                givenAuctionContextWithHeaders(bidRequest, Map.of(X_FORWARDED_FOR_HEADER, "1.2.3.4"));

        // when
        final OptableAttributes result = OptableAttributesResolver.resolveAttributes(
                auctionContext, bidRequest, properties.getTimeout(), 0.01, ipAddressHelper);

        // then
        assertThat(result).isNotNull()
                .returns(List.of("1.2.3.4"), OptableAttributes::getIps);
    }

    @Test
    public void shouldMaskIpv4FromXForwardedForHeaderWhenTransmitUfpdIsNotAllowed() {
        // given
        when(activityInfrastructure.isAllowed(eq(Activity.TRANSMIT_UFPD), any())).thenReturn(false);
        when(ipAddressHelper.maskIpv4("1.2.3.4")).thenReturn("1.2.3.0");
        final BidRequest bidRequest = givenBidRequest();
        final AuctionContext auctionContext =
                givenAuctionContextWithHeaders(bidRequest, Map.of(X_FORWARDED_FOR_HEADER, "1.2.3.4"));

        // when
        final OptableAttributes result = OptableAttributesResolver.resolveAttributes(
                auctionContext, bidRequest, properties.getTimeout(), 0.01, ipAddressHelper);

        // then
        assertThat(result).isNotNull()
                .returns(List.of("1.2.3.0"), OptableAttributes::getIps);
    }

    @Test
    public void shouldAnonymizeIpv6FromXForwardedForHeaderWhenTransmitUfpdIsNotAllowed() {
        // given
        when(activityInfrastructure.isAllowed(eq(Activity.TRANSMIT_UFPD), any())).thenReturn(false);
        when(ipAddressHelper.anonymizeIpv6("2001:db8::1")).thenReturn("2001:db8::");
        final BidRequest bidRequest = givenBidRequest();
        final AuctionContext auctionContext =
                givenAuctionContextWithHeaders(bidRequest, Map.of(X_FORWARDED_FOR_HEADER, "2001:db8::1"));

        // when
        final OptableAttributes result = OptableAttributesResolver.resolveAttributes(
                auctionContext, bidRequest, properties.getTimeout(), 0.01, ipAddressHelper);

        // then
        assertThat(result).isNotNull()
                .returns(List.of("2001:db8::"), OptableAttributes::getIps);
    }

    @Test
    public void shouldResolveIpsFromMaskedDeviceRatherThanFromAuctionContextBidRequest() {
        // given
        final BidRequest rawBidRequest = BidRequest.builder()
                .device(Device.builder().ip("1.2.3.4").ipv6("2001:db8::1").ua("userAgent").build())
                .build();
        final AuctionContext auctionContext = givenAuctionContext(rawBidRequest, tcfContext, gppContext);
        final BidRequest maskedBidRequest = rawBidRequest.toBuilder()
                .device(Device.builder().ip("1.2.3.0").ua("userAgent").build())
                .build();

        // when
        final OptableAttributes result = OptableAttributesResolver.resolveAttributes(
                auctionContext, maskedBidRequest, properties.getTimeout(), 0.01, ipAddressHelper);

        // then
        assertThat(result).isNotNull()
                .returns(List.of("1.2.3.0"), OptableAttributes::getIps)
                .returns("userAgent", OptableAttributes::getUserAgent);
    }

    @Test
    public void shouldNotResolveId5SignatureWhenMaskedUserExtIsDropped() {
        // given
        final BidRequest rawBidRequest = givenBidRequestWithId5Signature("signature");
        final AuctionContext auctionContext = givenAuctionContext(rawBidRequest, tcfContext, gppContext);
        final BidRequest maskedBidRequest = rawBidRequest.toBuilder()
                .user(null)
                .build();

        // when
        final OptableAttributes result = OptableAttributesResolver.resolveAttributes(
                auctionContext, maskedBidRequest, properties.getTimeout(), 0.01, ipAddressHelper);

        // then
        assertThat(result).isNotNull()
                .returns(null, OptableAttributes::getId5Signature);
    }

    private BidRequest givenBidRequestWithId5Signature(String signature) {
        final ObjectNode optable = mapper.createObjectNode();
        if (StringUtils.isNotEmpty(signature)) {
            optable.set("id5_signature", TextNode.valueOf(signature));
        }

        final ExtUser extUser = ExtUser.builder().build();
        extUser.addProperty("optable", optable);
        final User user = User.builder().ext(extUser).build();

        return BidRequest.builder().user(user).build();
    }

    private BidRequest givenBidRequestWithGdprORTB26(boolean isGdprEnabled, String consent) {
        final User user = User.builder()
                .consent(consent)
                .build();

        return BidRequest.builder()
                .user(user)
                .regs(Regs.builder()
                        .gdpr(isGdprEnabled ? 1 : 0)
                        .build())
                .build();
    }

    private BidRequest givenBidRequestWithGdprORTB25(boolean isGdprEnabled, String consent) {
        final User user = User.builder()
                .ext(ExtUser.builder()
                        .consent(consent)
                        .build())
                .build();

        return BidRequest.builder()
                .user(user)
                .regs(Regs.builder()
                        .ext(ExtRegs.of(isGdprEnabled ? 1 : 0, null, null, null))
                        .build())
                .build();
    }

    public AuctionContext givenAuctionContext(BidRequest bidRequest,
                                               TcfContext tcfContext,
                                               GppContext gppContext) {
        return AuctionContext.builder()
                .bidRequest(bidRequest)
                .privacyContext(PrivacyContext.of(Privacy.builder().build(), tcfContext, "8.8.8.8"))
                .gppContext(gppContext)
                .build();
    }

    public AuctionContext givenAuctionContextWithHeaders(BidRequest bidRequest, Map<String, String> headers) {
        return AuctionContext.builder()
                .bidRequest(bidRequest)
                .httpRequest(givenHttpRequestContext(headers))
                .activityInfrastructure(activityInfrastructure)
                .build();
    }
}
