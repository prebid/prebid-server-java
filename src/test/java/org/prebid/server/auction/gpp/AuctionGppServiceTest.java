package org.prebid.server.auction.gpp;

import com.iab.gpp.encoder.GppModel;
import com.iab.gpp.encoder.error.EncodingException;
import com.iab.openrtb.request.BidRequest;
import com.iab.openrtb.request.Regs;
import com.iab.openrtb.request.User;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.mockito.ArgumentMatcher;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnit;
import org.mockito.junit.MockitoRule;
import org.prebid.server.auction.gpp.model.GppContext;
import org.prebid.server.auction.gpp.model.GppContextCreator;
import org.prebid.server.auction.gpp.model.privacy.TcfEuV2Privacy;
import org.prebid.server.auction.gpp.model.privacy.UspV1Privacy;
import org.prebid.server.auction.model.AuctionContext;
import org.prebid.server.auction.model.debug.DebugContext;
import org.prebid.server.proto.openrtb.ext.request.TraceLevel;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.BDDMockito.given;

public class AuctionGppServiceTest {

    @Rule
    public final MockitoRule mockitoRule = MockitoJUnit.rule();

    @Mock
    private GppService gppService;

    private AuctionGppService auctionGppService;

    @Before
    public void setUp() {
        auctionGppService = new AuctionGppService(gppService);
    }

    @Test
    public void applyShouldReturnSameBidRequest() {
        // given
        given(gppService.processContext(
                argThat(gppContextMatcher(
                        Set.of(2, 6),
                        TcfEuV2Privacy.of(1, "consent"),
                        UspV1Privacy.of("usPrivacy"),
                        Collections.emptyList()))))
                .willReturn(givenGppContext(
                        List.of(2, 6),
                        TcfEuV2Privacy.of(2, "gppConsent"),
                        UspV1Privacy.of("gppUsPrivacy"),
                        Collections.emptyList()));

        final BidRequest bidRequest = givenBidRequest(
                givenValidGppString(), List.of(2, 6), 1, "consent", "usPrivacy");
        final AuctionContext auctionContext = givenAuctionContext(true);

        // when
        final BidRequest result = auctionGppService.apply(bidRequest, auctionContext);

        // then
        assertThat(result).isSameAs(bidRequest);
        assertThat(auctionContext.getDebugWarnings()).isEmpty();
    }

    @Test
    public void applyShouldUpdateBidRequestGdpr() {
        // given
        given(gppService.processContext(any()))
                .willReturn(givenGppContext(
                        List.of(2, 6),
                        TcfEuV2Privacy.of(2, "gppConsent"),
                        UspV1Privacy.of("gppUsPrivacy"),
                        Collections.emptyList()));

        final BidRequest bidRequest = givenBidRequest(
                givenValidGppString(), List.of(2, 6), null, "consent", "usPrivacy");
        final AuctionContext auctionContext = givenAuctionContext(true);

        // when
        final BidRequest result = auctionGppService.apply(bidRequest, auctionContext);

        // then
        assertThat(result.getUser()).isSameAs(bidRequest.getUser());
        assertThat(result.getRegs()).satisfies(regs -> {
            assertThat(regs.getGdpr()).isEqualTo(2);
            assertThat(regs.getUsPrivacy()).isSameAs(bidRequest.getRegs().getUsPrivacy());
        });
        assertThat(auctionContext.getDebugWarnings()).isEmpty();
    }

    @Test
    public void applyShouldUpdateBidRequestConsent() {
        // given
        given(gppService.processContext(any()))
                .willReturn(givenGppContext(
                        List.of(2, 6),
                        TcfEuV2Privacy.of(2, "gppConsent"),
                        UspV1Privacy.of("gppUsPrivacy"),
                        Collections.emptyList()));

        final BidRequest bidRequest = givenBidRequest(
                givenValidGppString(), List.of(2, 6), 1, null, "usPrivacy");
        final AuctionContext auctionContext = givenAuctionContext(true);

        // when
        final BidRequest result = auctionGppService.apply(bidRequest, auctionContext);

        // then
        assertThat(result.getUser()).isEqualTo(User.builder().consent("gppConsent").build());
        assertThat(result.getRegs()).isSameAs(bidRequest.getRegs());
        assertThat(auctionContext.getDebugWarnings()).isEmpty();
    }

    @Test
    public void applyShouldUpdateBidRequestUsPrivacy() {
        // given
        given(gppService.processContext(any()))
                .willReturn(givenGppContext(
                        List.of(2, 6),
                        TcfEuV2Privacy.of(2, "gppConsent"),
                        UspV1Privacy.of("gppUsPrivacy"),
                        Collections.emptyList()));

        final BidRequest bidRequest = givenBidRequest(
                givenValidGppString(), List.of(2, 6), 1, "consent", null);
        final AuctionContext auctionContext = givenAuctionContext(true);

        // when
        final BidRequest result = auctionGppService.apply(bidRequest, auctionContext);

        // then
        assertThat(result.getUser()).isSameAs(bidRequest.getUser());
        assertThat(result.getRegs()).satisfies(regs -> {
            assertThat(regs.getGdpr()).isSameAs(bidRequest.getRegs().getGdpr());
            assertThat(regs.getUsPrivacy()).isEqualTo("gppUsPrivacy");
        });
        assertThat(auctionContext.getDebugWarnings()).isEmpty();
    }

    @Test
    public void applyShouldUpdateBidRequest() {
        // given
        given(gppService.processContext(any()))
                .willReturn(givenGppContext(
                        List.of(2, 6),
                        TcfEuV2Privacy.of(2, "gppConsent"),
                        UspV1Privacy.of("gppUsPrivacy"),
                        Collections.emptyList()));

        final BidRequest bidRequest = givenBidRequest(
                givenValidGppString(), List.of(2, 6), null, null, null);
        final AuctionContext auctionContext = givenAuctionContext(true);

        // when
        final BidRequest result = auctionGppService.apply(bidRequest, auctionContext);

        // then
        assertThat(result.getUser()).isEqualTo(User.builder().consent("gppConsent").build());
        assertThat(result.getRegs()).satisfies(regs -> {
            assertThat(regs.getGdpr()).isEqualTo(2);
            assertThat(regs.getUsPrivacy()).isEqualTo("gppUsPrivacy");
        });
        assertThat(auctionContext.getDebugWarnings()).isEmpty();
    }

    @Test
    public void applyShouldAddWarningsInAuctionContextIfDebugEnabled() {
        // given
        given(gppService.processContext(
                argThat(gppContextMatcher(
                        Set.of(2, 6),
                        TcfEuV2Privacy.of(1, "consent"),
                        UspV1Privacy.of("usPrivacy"),
                        Collections.emptyList()))))
                .willReturn(givenGppContext(
                        List.of(2, 6),
                        TcfEuV2Privacy.of(2, "gppConsent"),
                        UspV1Privacy.of("gppUsPrivacy"),
                        List.of("warning")));

        final BidRequest bidRequest = givenBidRequest(
                givenValidGppString(), List.of(2, 6), 1, "consent", "usPrivacy");
        final AuctionContext auctionContext = givenAuctionContext(true);

        // when
        final BidRequest result = auctionGppService.apply(bidRequest, auctionContext);

        // then
        assertThat(result).isSameAs(bidRequest);
        assertThat(auctionContext.getDebugWarnings()).containsExactly("warning");
    }

    @Test
    public void applyShouldNotAddWarningsInAuctionContextIfDebugDisabled() {
        // given
        given(gppService.processContext(
                argThat(gppContextMatcher(
                        Set.of(2, 6),
                        TcfEuV2Privacy.of(1, "consent"),
                        UspV1Privacy.of("usPrivacy"),
                        Collections.emptyList()))))
                .willReturn(givenGppContext(
                        List.of(2, 6),
                        TcfEuV2Privacy.of(2, "gppConsent"),
                        UspV1Privacy.of("gppUsPrivacy"),
                        List.of("warning")));

        final BidRequest bidRequest = givenBidRequest(
                givenValidGppString(), List.of(2, 6), 1, "consent", "usPrivacy");
        final AuctionContext auctionContext = givenAuctionContext(false);

        // when
        final BidRequest result = auctionGppService.apply(bidRequest, auctionContext);

        // then
        assertThat(result).isSameAs(bidRequest);
        assertThat(auctionContext.getDebugWarnings()).isEmpty();
    }

    private static ArgumentMatcher<GppContext> gppContextMatcher(Set<Integer> sectionsIds,
                                                                 TcfEuV2Privacy tcfEuV2Privacy,
                                                                 UspV1Privacy uspV1Privacy,
                                                                 List<String> errors) {

        return gppContext -> Objects.equals(gppContext.scope().getSectionsIds(), sectionsIds)
                && Objects.equals(gppContext.regions().getTcfEuV2Privacy(), tcfEuV2Privacy)
                && Objects.equals(gppContext.regions().getUspV1Privacy(), uspV1Privacy)
                && Objects.equals(gppContext.errors(), errors);
    }

    private static GppContext givenGppContext(List<Integer> sectionsIds,
                                              TcfEuV2Privacy tcfEuV2Privacy,
                                              UspV1Privacy uspV1Privacy,
                                              List<String> errors) {

        final GppContext gppContext = GppContextCreator.from(givenValidGppString(), sectionsIds)
                .with(tcfEuV2Privacy)
                .with(uspV1Privacy)
                .build();
        gppContext.errors().addAll(errors);

        return gppContext;
    }

    private static String givenValidGppString() {
        try {
            return new GppModel().encode();
        } catch (EncodingException e) {
            throw new RuntimeException(e);
        }
    }

    private static BidRequest givenBidRequest(String gpp,
                                              List<Integer> gppSid,
                                              Integer gdpr,
                                              String consent,
                                              String usPrivacy) {

        return BidRequest.builder()
                .user(User.builder().consent(consent).build())
                .regs(Regs.builder().gpp(gpp).gppSid(gppSid).gdpr(gdpr).usPrivacy(usPrivacy).build())
                .build();
    }

    private static AuctionContext givenAuctionContext(boolean debug) {
        return AuctionContext.builder()
                .debugContext(DebugContext.of(debug, false, TraceLevel.basic))
                .debugWarnings(new ArrayList<>())
                .build();
    }
}
