package org.prebid.server.auction.versionconverter.down;

import com.fasterxml.jackson.databind.node.TextNode;
import com.iab.openrtb.request.App;
import com.iab.openrtb.request.Audio;
import com.iab.openrtb.request.BidRequest;
import com.iab.openrtb.request.Channel;
import com.iab.openrtb.request.Content;
import com.iab.openrtb.request.Device;
import com.iab.openrtb.request.Dooh;
import com.iab.openrtb.request.Eid;
import com.iab.openrtb.request.Imp;
import com.iab.openrtb.request.Network;
import com.iab.openrtb.request.Producer;
import com.iab.openrtb.request.Publisher;
import com.iab.openrtb.request.Qty;
import com.iab.openrtb.request.RefSettings;
import com.iab.openrtb.request.Refresh;
import com.iab.openrtb.request.Regs;
import com.iab.openrtb.request.Site;
import com.iab.openrtb.request.Source;
import com.iab.openrtb.request.SupplyChain;
import com.iab.openrtb.request.User;
import com.iab.openrtb.request.UserAgent;
import com.iab.openrtb.request.Video;
import org.junit.Test;
import org.prebid.server.VertxTest;
import org.prebid.server.proto.openrtb.ext.request.ExtRegs;
import org.prebid.server.proto.openrtb.ext.request.ExtRegsDsa;
import org.prebid.server.proto.openrtb.ext.request.ExtSource;
import org.prebid.server.proto.openrtb.ext.request.ExtUser;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.function.UnaryOperator;

import static java.util.Arrays.asList;
import static java.util.Collections.emptyList;
import static java.util.Collections.singletonList;
import static org.assertj.core.api.Assertions.assertThat;
import static org.prebid.server.hooks.v1.PayloadUpdate.identity;

public class BidRequestOrtb26To25ConverterTest extends VertxTest {

    private final BidRequestOrtb26To25Converter target = new BidRequestOrtb26To25Converter(jacksonMapper);

    @Test
    public void convertShouldMoveImpsRwdd() {
        // given
        final BidRequest bidRequest = givenBidRequest(request -> request.imp(asList(
                givenImp(identity()),
                givenImp(imp -> imp.rwdd(1)),
                givenImp(imp -> imp.rwdd(0)
                        .ext(mapper.valueToTree(Map.of("prebid", Map.of("someField", "someValue"))))))));

        // when
        final BidRequest result = target.convert(bidRequest);

        // then
        assertThat(result)
                .extracting(BidRequest::getImp)
                .satisfies(imps -> {
                    assertThat(imps)
                            .extracting(Imp::getRwdd)
                            .containsOnlyNulls();
                    assertThat(imps)
                            .extracting(Imp::getExt)
                            .containsExactly(
                                    null,
                                    mapper.valueToTree(Map.of("prebid", Map.of("is_rewarded_inventory", 1))),
                                    mapper.valueToTree(Map.of(
                                            "prebid", Map.of(
                                                    "is_rewarded_inventory", 0,
                                                    "someField", "someValue"))));
                });
    }

    @Test
    public void convertShouldMoveSourceSupplyChain() {
        // given
        final SupplyChain supplyChain = SupplyChain.of(0, emptyList(), "", null);
        final BidRequest bidRequest = givenBidRequest(request -> request.source(
                Source.builder()
                        .schain(supplyChain)
                        .ext(mapper.convertValue(Map.of("someField", "someValue"), ExtSource.class))
                        .build()));

        // when
        final BidRequest result = target.convert(bidRequest);

        // then
        assertThat(result)
                .extracting(BidRequest::getSource)
                .satisfies(source -> {
                    assertThat(source)
                            .extracting(Source::getSchain)
                            .isNull();

                    final ExtSource expectedSourceExt = ExtSource.of(supplyChain);
                    expectedSourceExt.addProperty("someField", TextNode.valueOf("someValue"));
                    assertThat(source)
                            .extracting(Source::getExt)
                            .isEqualTo(expectedSourceExt);
                });
    }

    @Test
    public void convertShouldMoveRegsData() {
        // given
        final Map<String, Object> dsaMap = Map.of(
                "dsarequired", 1,
                "pubrender", 2,
                "datatopub", 3,
                "transparency", emptyList());
        final BidRequest bidRequest = givenBidRequest(request -> request.regs(
                Regs.builder()
                        .gdpr(1)
                        .usPrivacy("usPrivacy")
                        .ext(mapper.convertValue(
                                Map.of(
                                        "someField", "someValue",
                                        "gpc", "1",
                                        "dsa", dsaMap),
                                ExtRegs.class))
                        .build()));

        // when
        final BidRequest result = target.convert(bidRequest);

        // then
        assertThat(result)
                .extracting(BidRequest::getRegs)
                .satisfies(regs -> {
                    assertThat(regs)
                            .extracting(Regs::getGdpr, Regs::getUsPrivacy)
                            .containsOnlyNulls();

                    final ExtRegsDsa dsa = ExtRegsDsa.of(1, 2, 3, emptyList());
                    final ExtRegs expectedRegsExt = ExtRegs.of(1, "usPrivacy", "1", dsa);
                    expectedRegsExt.addProperty("someField", TextNode.valueOf("someValue"));
                    assertThat(regs)
                            .extracting(Regs::getExt)
                            .isEqualTo(expectedRegsExt);
                });
    }

    @Test
    public void convertShouldRemoveRegsGppData() {
        // given
        final BidRequest bidRequest = givenBidRequest(request -> request.regs(
                Regs.builder()
                        .gpp("gppValue")
                        .gppSid(List.of(1, 2, 3))
                        .build()));

        // when
        final BidRequest result = target.convert(bidRequest);

        // then
        assertThat(result)
                .extracting(BidRequest::getRegs)
                .extracting(Regs::getGpp, Regs::getGppSid)
                .containsOnlyNulls();
    }

    @Test
    public void convertShouldMoveUserData() {
        // given
        final BidRequest bidRequest = givenBidRequest(request -> request.user(
                User.builder()
                        .consent("consent")
                        .eids(singletonList(Eid.of("source", emptyList(), null)))
                        .ext(mapper.convertValue(Map.of("someField", "someValue"), ExtUser.class))
                        .build()));

        // when
        final BidRequest result = target.convert(bidRequest);

        // then
        assertThat(result)
                .extracting(BidRequest::getUser)
                .satisfies(user -> {
                    assertThat(user)
                            .extracting(User::getConsent, User::getEids)
                            .containsOnlyNulls();

                    final ExtUser expectedUserExt = ExtUser.builder()
                            .consent("consent")
                            .eids(singletonList(Eid.of("source", emptyList(), null)))
                            .build();
                    expectedUserExt.addProperty("someField", TextNode.valueOf("someValue"));
                    assertThat(user)
                            .extracting(User::getExt)
                            .isEqualTo(expectedUserExt);
                });
    }

    @Test
    public void convertShouldRemoveFieldsThatAreNotInOrtb25() {
        // given
        final BidRequest bidRequest = givenBidRequest(request -> request
                .imp(singletonList(givenImp(imp -> imp
                        .video(Video.builder()
                                .maxseq(1)
                                .poddur(1)
                                .podid(1)
                                .podseq(1)
                                .rqddurs(singletonList(1))
                                .slotinpod(1)
                                .mincpmpersec(BigDecimal.ONE)
                                .build())
                        .audio(Audio.builder()
                                .poddur(1)
                                .rqddurs(singletonList(1))
                                .podid(1)
                                .podseq(1)
                                .slotinpod(1)
                                .mincpmpersec(BigDecimal.ONE)
                                .maxseq(1)
                                .build())
                        .refresh(Refresh.builder().count(1)
                                .refsettings(singletonList(RefSettings.builder().minint(1).build()))
                                .build())
                        .qty(Qty.builder().multiplier(BigDecimal.ONE).build())
                        .dt(0.1)
                        .ssai(1))))
                .site(Site.builder()
                        .cattax(1)
                        .inventorypartnerdomain("inventorypartnerdomain")
                        .publisher(Publisher.builder().cattax(1).build())
                        .content(Content.builder()
                                .producer(Producer.builder().cattax(1).build())
                                .cattax(1)
                                .kwarray(singletonList("kwarray"))
                                .langb("langb")
                                .network(Network.builder().build())
                                .channel(Channel.builder().build())
                                .build())
                        .kwarray(singletonList("kwarray"))
                        .build())
                .app(App.builder()
                        .cattax(1)
                        .inventorypartnerdomain("inventorypartnerdomain")
                        .publisher(Publisher.builder().cattax(1).build())
                        .content(Content.builder()
                                .producer(Producer.builder().cattax(1).build())
                                .cattax(1)
                                .kwarray(singletonList("kwarray"))
                                .langb("langb")
                                .network(Network.builder().build())
                                .channel(Channel.builder().build())
                                .build())
                        .kwarray(singletonList("kwarray"))
                        .build())
                .dooh(Dooh.builder().build())
                .device(Device.builder().sua(UserAgent.builder().build()).langb("langb").build())
                .user(User.builder().kwarray(singletonList("kwarray")).build())
                .wlangb(singletonList("wlangb"))
                .cattax(1));

        // when
        final BidRequest result = target.convert(bidRequest);

        // then
        assertThat(result).satisfies(request -> {
            assertThat(result.getImp())
                    .hasSize(1)
                    .allSatisfy(imp -> {
                        assertThat(imp.getVideo()).isEqualTo(Video.builder().build());
                        assertThat(imp.getAudio()).isEqualTo(Audio.builder().build());
                        assertThat(imp.getSsai()).isNull();
                        assertThat(imp.getRefresh()).isNull();
                        assertThat(imp.getQty()).isNull();
                        assertThat(imp.getDt()).isNull();
                    });

            assertThat(request.getSite()).isEqualTo(Site.builder().build());
            assertThat(request.getApp()).isEqualTo(App.builder().build());
            assertThat(request.getDevice()).isEqualTo(Device.builder().build());
            assertThat(request.getUser()).isEqualTo(User.builder().build());
            assertThat(request.getWlangb()).isNull();
            assertThat(request.getCattax()).isNull();
        });
    }

    private static BidRequest givenBidRequest(UnaryOperator<BidRequest.BidRequestBuilder> bidRequestCustomizer) {
        return bidRequestCustomizer.apply(BidRequest.builder().imp(emptyList())).build();
    }

    private static Imp givenImp(UnaryOperator<Imp.ImpBuilder> impCustomizer) {
        return impCustomizer.apply(Imp.builder()).build();
    }
}
