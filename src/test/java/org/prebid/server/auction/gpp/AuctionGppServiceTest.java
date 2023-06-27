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
import org.prebid.server.auction.gpp.model.GppContextWrapper;
import org.prebid.server.auction.gpp.model.privacy.TcfEuV2Privacy;
import org.prebid.server.auction.gpp.model.privacy.UspV1Privacy;
import org.prebid.server.auction.model.AuctionContext;
import org.prebid.server.auction.model.debug.DebugContext;
import org.prebid.server.proto.openrtb.ext.request.ExtRegs;
import org.prebid.server.proto.openrtb.ext.request.ExtUser;
import org.prebid.server.proto.openrtb.ext.request.TraceLevel;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.Set;

import static java.util.Collections.singletonList;
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
    public void contextFromShouldReturnExpectedGppContext() {
        // given
        given(gppService.processContext(
                argThat(gppContextWrapperMatcher(
                        Set.of(2, 6),
                        TcfEuV2Privacy.of(1, "consent"),
                        UspV1Privacy.of("usPrivacy"),
                        Collections.emptyList()))))
                .willReturn(givenGppContextWrapper(
                        List.of(2, 6),
                        TcfEuV2Privacy.of(2, "gppConsent"),
                        UspV1Privacy.of("gppUsPrivacy"),
                        Collections.emptyList()));

        final BidRequest bidRequest = givenBidRequest(
                givenValidGppString(), List.of(2, 6), 1, "consent", "usPrivacy");
        final AuctionContext auctionContext = givenAuctionContext(bidRequest, true);

        // when
        final GppContext result = auctionGppService.contextFrom(auctionContext).result();

        // then
        assertThat(result.scope().getSectionsIds()).containsExactlyInAnyOrder(2, 6);
        assertThat(result.regions().getTcfEuV2Privacy()).isEqualTo(TcfEuV2Privacy.of(2, "gppConsent"));
        assertThat(result.regions().getUspV1Privacy()).isEqualTo(UspV1Privacy.of("gppUsPrivacy"));
        assertThat(auctionContext.getDebugWarnings()).isEmpty();
    }

    @Test
    public void contextFromShouldReturnExpectedGppContextUsingLegacyPlaces() {
        // given
        given(gppService.processContext(any()))
                .willAnswer(invocation -> invocation.getArgument(0));

        final BidRequest bidRequest = givenLegacyBidRequest(
                givenValidGppString(), List.of(2, 6), 1, "consent", "usPrivacy");
        final AuctionContext auctionContext = givenAuctionContext(bidRequest, true);

        // when
        final GppContext result = auctionGppService.contextFrom(auctionContext).result();

        // then
        assertThat(result.scope().getSectionsIds()).containsExactlyInAnyOrder(2, 6);
        assertThat(result.regions().getTcfEuV2Privacy()).isEqualTo(TcfEuV2Privacy.of(1, "consent"));
        assertThat(result.regions().getUspV1Privacy()).isEqualTo(UspV1Privacy.of("usPrivacy"));
        assertThat(auctionContext.getDebugWarnings()).isEmpty();
    }

    @Test
    public void contextFromShouldAddWarningsInAuctionContextIfDebugEnabled() {
        // given
        given(gppService.processContext(any()))
                .willReturn(givenGppContextWrapper(
                        List.of(2, 6),
                        TcfEuV2Privacy.of(2, "gppConsent"),
                        UspV1Privacy.of("gppUsPrivacy"),
                        singletonList("Ups, something went wrong")));

        final BidRequest bidRequest = givenBidRequest(
                givenValidGppString(), List.of(2, 6), 1, "consent", "usPrivacy");
        final AuctionContext auctionContext = givenAuctionContext(bidRequest, true);

        // when
        auctionGppService.contextFrom(auctionContext);

        // then
        assertThat(auctionContext.getDebugWarnings()).containsExactly("Ups, something went wrong");
    }

    @Test
    public void contextFromShouldNotAddWarningsInAuctionContextIfDebugDisabled() {
        // given
        given(gppService.processContext(any()))
                .willReturn(givenGppContextWrapper(
                        List.of(2, 6),
                        TcfEuV2Privacy.of(2, "gppConsent"),
                        UspV1Privacy.of("gppUsPrivacy"),
                        singletonList("Ups, something went wrong")));

        final BidRequest bidRequest = givenBidRequest(
                givenValidGppString(), List.of(2, 6), 1, "consent", "usPrivacy");
        final AuctionContext auctionContext = givenAuctionContext(bidRequest, false);

        // when
        auctionGppService.contextFrom(auctionContext);

        // then
        assertThat(auctionContext.getDebugWarnings()).isEmpty();
    }

    @Test
    public void updateBidRequestShouldReturnSameBidRequest() {
        // given
        final BidRequest bidRequest = givenBidRequest(
                givenValidGppString(), List.of(2, 6), 1, "consent", "usPrivacy");
        final AuctionContext auctionContext = givenAuctionContext(givenGppContext(
                List.of(2, 6),
                TcfEuV2Privacy.of(2, "gppConsent"),
                UspV1Privacy.of("gppUsPrivacy")));

        // when
        final BidRequest result = auctionGppService.updateBidRequest(bidRequest, auctionContext);

        // then
        assertThat(result).isSameAs(bidRequest);
    }

    @Test
    public void updateBidRequestShouldUpdateBidRequestGdpr() {
        // given
        final BidRequest bidRequest = givenBidRequest(
                givenValidGppString(), List.of(2, 6), null, "consent", "usPrivacy");
        final AuctionContext auctionContext = givenAuctionContext(givenGppContext(
                List.of(2, 6),
                TcfEuV2Privacy.of(2, "gppConsent"),
                null));

        // when
        final BidRequest result = auctionGppService.updateBidRequest(bidRequest, auctionContext);

        // then
        assertThat(result.getUser()).isSameAs(bidRequest.getUser());
        assertThat(result.getRegs()).satisfies(regs -> {
            assertThat(regs.getGdpr()).isEqualTo(2);
            assertThat(regs.getUsPrivacy()).isSameAs(bidRequest.getRegs().getUsPrivacy());
        });
        assertThat(auctionContext.getDebugWarnings()).isEmpty();
    }

    @Test
    public void updateBidRequestShouldUpdateBidRequestConsent() {
        // given
        final BidRequest bidRequest = givenBidRequest(
                givenValidGppString(), List.of(2, 6), 1, null, "usPrivacy");
        final AuctionContext auctionContext = givenAuctionContext(givenGppContext(
                List.of(2, 6),
                TcfEuV2Privacy.of(2, "gppConsent"),
                UspV1Privacy.of("gppUsPrivacy")));

        // when
        final BidRequest result = auctionGppService.updateBidRequest(bidRequest, auctionContext);

        // then
        assertThat(result.getUser()).isEqualTo(User.builder().consent("gppConsent").build());
        assertThat(result.getRegs()).isSameAs(bidRequest.getRegs());
        assertThat(auctionContext.getDebugWarnings()).isEmpty();
    }

    @Test
    public void updateBidRequestShouldUpdateBidRequestUsPrivacy() {
        // given
        final BidRequest bidRequest = givenBidRequest(
                givenValidGppString(), List.of(2, 6), 1, "consent", null);
        final AuctionContext auctionContext = givenAuctionContext(givenGppContext(
                List.of(2, 6),
                null,
                UspV1Privacy.of("gppUsPrivacy")));

        // when
        final BidRequest result = auctionGppService.updateBidRequest(bidRequest, auctionContext);

        // then
        assertThat(result.getUser()).isSameAs(bidRequest.getUser());
        assertThat(result.getRegs()).satisfies(regs -> {
            assertThat(regs.getGdpr()).isSameAs(bidRequest.getRegs().getGdpr());
            assertThat(regs.getUsPrivacy()).isEqualTo("gppUsPrivacy");
        });
        assertThat(auctionContext.getDebugWarnings()).isEmpty();
    }

    @Test
    public void updateBidRequestShouldReturnExpectedResult() {
        // given
        final BidRequest bidRequest = givenBidRequest(
                givenValidGppString(), List.of(2, 6), null, null, null);
        final AuctionContext auctionContext = givenAuctionContext(givenGppContext(
                List.of(2, 6),
                TcfEuV2Privacy.of(2, "gppConsent"),
                UspV1Privacy.of("gppUsPrivacy")));

        // when
        final BidRequest result = auctionGppService.updateBidRequest(bidRequest, auctionContext);

        // then
        assertThat(result.getUser()).isEqualTo(User.builder().consent("gppConsent").build());
        assertThat(result.getRegs()).satisfies(regs -> {
            assertThat(regs.getGdpr()).isEqualTo(2);
            assertThat(regs.getUsPrivacy()).isEqualTo("gppUsPrivacy");
        });
        assertThat(auctionContext.getDebugWarnings()).isEmpty();
    }

    private static ArgumentMatcher<GppContextWrapper> gppContextWrapperMatcher(Set<Integer> sectionsIds,
                                                                               TcfEuV2Privacy tcfEuV2Privacy,
                                                                               UspV1Privacy uspV1Privacy,
                                                                               List<String> errors) {

        return wrapper -> Objects.equals(wrapper.getGppContext().scope().getSectionsIds(), sectionsIds)
                && Objects.equals(wrapper.getGppContext().regions().getTcfEuV2Privacy(), tcfEuV2Privacy)
                && Objects.equals(wrapper.getGppContext().regions().getUspV1Privacy(), uspV1Privacy)
                && Objects.equals(wrapper.getErrors(), errors);
    }

    private static GppContextWrapper givenGppContextWrapper(List<Integer> sectionsIds,
                                                            TcfEuV2Privacy tcfEuV2Privacy,
                                                            UspV1Privacy uspV1Privacy,
                                                            List<String> errors) {

        final GppContextWrapper gppContextWrapper = GppContextCreator.from(givenValidGppString(), sectionsIds)
                .with(tcfEuV2Privacy)
                .with(uspV1Privacy)
                .build();
        gppContextWrapper.getErrors().addAll(errors);

        return gppContextWrapper;
    }

    private static GppContext givenGppContext(List<Integer> sectionsIds,
                                              TcfEuV2Privacy tcfEuV2Privacy,
                                              UspV1Privacy uspV1Privacy) {

        return givenGppContextWrapper(sectionsIds, tcfEuV2Privacy, uspV1Privacy, Collections.emptyList())
                .getGppContext();
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

    private static BidRequest givenLegacyBidRequest(String gpp,
                                                    List<Integer> gppSid,
                                                    Integer gdpr,
                                                    String consent,
                                                    String usPrivacy) {

        return BidRequest.builder()
                .user(User.builder().ext(ExtUser.builder().consent(consent).build()).build())
                .regs(Regs.builder().gpp(gpp).gppSid(gppSid).ext(ExtRegs.of(gdpr, usPrivacy, null)).build())
                .build();
    }

    private static AuctionContext givenAuctionContext(BidRequest bidRequest, boolean debug) {
        return AuctionContext.builder()
                .bidRequest(bidRequest)
                .debugContext(DebugContext.of(debug, false, TraceLevel.basic))
                .debugWarnings(new ArrayList<>())
                .build();
    }

    private static AuctionContext givenAuctionContext(GppContext gppContext) {
        return AuctionContext.builder()
                .gppContext(gppContext)
                .debugWarnings(new ArrayList<>())
                .build();
    }
}
