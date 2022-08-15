package org.prebid.server.auction.versionconverter.up;

import com.fasterxml.jackson.databind.node.MissingNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.iab.openrtb.request.BidRequest;
import com.iab.openrtb.request.Eid;
import com.iab.openrtb.request.Imp;
import com.iab.openrtb.request.Regs;
import com.iab.openrtb.request.Source;
import com.iab.openrtb.request.SupplyChain;
import com.iab.openrtb.request.User;
import org.junit.Before;
import org.junit.Test;
import org.prebid.server.VertxTest;
import org.prebid.server.proto.openrtb.ext.request.ExtRegs;
import org.prebid.server.proto.openrtb.ext.request.ExtSource;
import org.prebid.server.proto.openrtb.ext.request.ExtUser;

import java.util.List;
import java.util.Map;
import java.util.function.UnaryOperator;

import static java.util.Arrays.asList;
import static java.util.Collections.emptyList;
import static java.util.Collections.singletonList;
import static org.assertj.core.api.Assertions.assertThat;

public class BidRequestOrtb25To26ConverterTest extends VertxTest {

    private BidRequestOrtb25To26Converter converter;

    @Before
    public void setUp() {
        converter = new BidRequestOrtb25To26Converter();
    }

    @Test
    public void convertShouldMoveImpsRwddIfNeeded() {
        // given
        final ObjectNode extImp = mapper.valueToTree(Map.of("prebid", Map.of("is_rewarded_inventory", 0)));
        final BidRequest bidRequest = givenBidRequest(request -> request.imp(asList(
                givenImp(imp -> imp.rwdd(1).ext(extImp)),
                givenImp(imp -> imp.ext(extImp)))));

        // when
        final BidRequest result = converter.convert(bidRequest);

        // then
        assertThat(result)
                .extracting(BidRequest::getImp)
                .satisfies(imps -> {
                    assertThat(imps)
                            .extracting(Imp::getRwdd)
                            .containsExactly(1, 0);
                    assertThat(imps)
                            .extracting(Imp::getExt)
                            .extracting(ext -> ext.at("/prebid/is_rewarded_inventory"))
                            .containsExactly(MissingNode.getInstance(), MissingNode.getInstance());
                });

    }

    @Test
    public void convertShouldMoveSourceExtSupplyChainToSourceSupplyChainIfNotPresent() {
        // given
        final SupplyChain supplyChain = SupplyChain.of(0, emptyList(), "", null);
        final BidRequest bidRequest = givenBidRequest(request -> request.source(
                Source.builder().ext(ExtSource.of(supplyChain)).build()));

        // when
        final BidRequest result = converter.convert(bidRequest);

        // then
        assertThat(result)
                .extracting(BidRequest::getSource)
                .satisfies(source -> {
                    assertThat(source)
                            .extracting(Source::getSchain)
                            .isSameAs(supplyChain);
                    assertThat(source)
                            .extracting(Source::getExt)
                            .isNull();
                });

    }

    @Test
    public void convertShouldNotChangeSourceSupplyChainIfPresent() {
        // given
        final SupplyChain supplyChain = SupplyChain.of(0, emptyList(), "", null);
        final BidRequest bidRequest = givenBidRequest(request -> request.source(
                Source.builder().schain(supplyChain).ext(ExtSource.of(SupplyChain.of(1, null, null, null))).build()));

        // when
        final BidRequest result = converter.convert(bidRequest);

        // then
        assertThat(result)
                .extracting(BidRequest::getSource)
                .satisfies(source -> {
                    assertThat(source)
                            .extracting(Source::getSchain)
                            .isSameAs(supplyChain);
                    assertThat(source)
                            .extracting(Source::getExt)
                            .isNull();
                });
    }

    @Test
    public void convertShouldMoveRegsExtGdprToRegsGdprIfNotPresent() {
        // given
        final Integer gdpr = 1;
        final BidRequest bidRequest = givenBidRequest(request -> request.regs(
                Regs.builder().ext(ExtRegs.of(gdpr, null)).build()));

        // when
        final BidRequest result = converter.convert(bidRequest);

        // then
        assertThat(result)
                .extracting(BidRequest::getRegs)
                .satisfies(regs -> {
                    assertThat(regs)
                            .extracting(Regs::getGdpr)
                            .isSameAs(gdpr);
                    assertThat(regs)
                            .extracting(Regs::getExt)
                            .isNull();
                });
    }

    @Test
    public void convertShouldNotChangeRegsGdprIfPresent() {
        // given
        final Integer gdpr = 1;
        final BidRequest bidRequest = givenBidRequest(request -> request.regs(
                Regs.builder().gdpr(gdpr).ext(ExtRegs.of(0, null)).build()));

        // when
        final BidRequest result = converter.convert(bidRequest);

        // then
        assertThat(result)
                .extracting(BidRequest::getRegs)
                .satisfies(regs -> {
                    assertThat(regs)
                            .extracting(Regs::getGdpr)
                            .isSameAs(gdpr);
                    assertThat(regs)
                            .extracting(Regs::getExt)
                            .isNull();
                });
    }

    @Test
    public void convertShouldMoveRegsExtUsPrivacyToRegsUsPrivacyIfNotPresent() {
        // given
        final String usPrivacy = "privacy";
        final BidRequest bidRequest = givenBidRequest(request -> request.regs(
                Regs.builder().ext(ExtRegs.of(null, usPrivacy)).build()));

        // when
        final BidRequest result = converter.convert(bidRequest);

        // then
        assertThat(result)
                .extracting(BidRequest::getRegs)
                .satisfies(regs -> {
                    assertThat(regs)
                            .extracting(Regs::getUsPrivacy)
                            .isSameAs(usPrivacy);
                    assertThat(regs)
                            .extracting(Regs::getExt)
                            .isNull();
                });
    }

    @Test
    public void convertShouldNotChangeRegsUsPrivacyIfPresent() {
        // given
        final String usPrivacy = "privacy";
        final BidRequest bidRequest = givenBidRequest(request -> request.regs(
                Regs.builder().usPrivacy(usPrivacy).ext(ExtRegs.of(null, "anotherPrivacy")).build()));

        // when
        final BidRequest result = converter.convert(bidRequest);

        // then
        assertThat(result)
                .extracting(BidRequest::getRegs)
                .satisfies(regs -> {
                    assertThat(regs)
                            .extracting(Regs::getUsPrivacy)
                            .isSameAs(usPrivacy);
                    assertThat(regs)
                            .extracting(Regs::getExt)
                            .isNull();
                });
    }

    @Test
    public void convertShouldMoveUserExtConsentToUserConsentIfNotPresent() {
        // given
        final String consent = "consent";
        final BidRequest bidRequest = givenBidRequest(request -> request.user(
                User.builder().ext(ExtUser.builder().consent(consent).build()).build()));

        // when
        final BidRequest result = converter.convert(bidRequest);

        // then
        assertThat(result)
                .extracting(BidRequest::getUser)
                .satisfies(user -> {
                    assertThat(user)
                            .extracting(User::getConsent)
                            .isSameAs(consent);
                    assertThat(user)
                            .extracting(User::getExt)
                            .isNull();
                });
    }

    @Test
    public void convertShouldNotChangeUserConsentIfPresent() {
        // given
        final String consent = "consent";
        final BidRequest bidRequest = givenBidRequest(request -> request.user(
                User.builder().consent(consent).ext(ExtUser.builder().consent("anotherConsent").build()).build()));

        // when
        final BidRequest result = converter.convert(bidRequest);

        // then
        assertThat(result)
                .extracting(BidRequest::getUser)
                .satisfies(user -> {
                    assertThat(user)
                            .extracting(User::getConsent)
                            .isSameAs(consent);
                    assertThat(user)
                            .extracting(User::getExt)
                            .isNull();
                });
    }

    @Test
    public void convertShouldMoveUserExtEidsToUserEidsIfNotPresent() {
        // given
        final List<Eid> eids = singletonList(Eid.of("source", emptyList(), null));
        final BidRequest bidRequest = givenBidRequest(request -> request.user(
                User.builder().ext(ExtUser.builder().eids(eids).build()).build()));

        // when
        final BidRequest result = converter.convert(bidRequest);

        // then
        assertThat(result)
                .extracting(BidRequest::getUser)
                .satisfies(user -> {
                    assertThat(user)
                            .extracting(User::getEids)
                            .isSameAs(eids);
                    assertThat(user)
                            .extracting(User::getExt)
                            .isNull();
                });
    }

    @Test
    public void convertShouldNotChangeUserEidsIfPresent() {
        // given
        final List<Eid> eids = singletonList(Eid.of("source", emptyList(), null));
        final BidRequest bidRequest = givenBidRequest(request -> request.user(
                User.builder().eids(eids).ext(ExtUser.builder().eids(emptyList()).build()).build()));

        // when
        final BidRequest result = converter.convert(bidRequest);

        // then
        assertThat(result)
                .extracting(BidRequest::getUser)
                .satisfies(user -> {
                    assertThat(user)
                            .extracting(User::getEids)
                            .isSameAs(eids);
                    assertThat(user)
                            .extracting(User::getExt)
                            .isNull();
                });
    }

    @Test
    public void convertShouldPass25EmptyEidArray() {
        // given
        final BidRequest bidRequest = givenBidRequest(request -> request.user(
                User.builder().ext(ExtUser.builder().eids(emptyList()).build()).build()));

        // when
        final BidRequest result = converter.convert(bidRequest);

        // then
        assertThat(result)
                .extracting(BidRequest::getUser)
                .satisfies(user -> {
                    assertThat(user)
                            .extracting(User::getEids)
                            .isSameAs(emptyList());
                    assertThat(user)
                            .extracting(User::getExt)
                            .isNull();
                });
    }

    private static BidRequest givenBidRequest(UnaryOperator<BidRequest.BidRequestBuilder> bidRequestCustomizer) {
        return bidRequestCustomizer.apply(BidRequest.builder()).build();
    }

    private static Imp givenImp(UnaryOperator<Imp.ImpBuilder> impCustomizer) {
        return impCustomizer.apply(Imp.builder()).build();
    }
}
