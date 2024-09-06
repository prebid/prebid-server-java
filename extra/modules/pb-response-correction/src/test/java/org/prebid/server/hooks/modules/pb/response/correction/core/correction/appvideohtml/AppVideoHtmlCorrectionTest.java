package org.prebid.server.hooks.modules.pb.response.correction.core.correction.appvideohtml;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.iab.openrtb.response.Bid;
import org.junit.jupiter.api.Test;
import org.prebid.server.auction.model.BidderResponse;
import org.prebid.server.bidder.model.BidderBid;
import org.prebid.server.bidder.model.BidderSeatBid;
import org.prebid.server.hooks.modules.pb.response.correction.core.config.model.AppVideoHtmlConfig;
import org.prebid.server.hooks.modules.pb.response.correction.core.config.model.Config;
import org.prebid.server.json.ObjectMapperProvider;
import org.prebid.server.proto.openrtb.ext.ExtPrebid;
import org.prebid.server.proto.openrtb.ext.response.BidType;
import org.prebid.server.proto.openrtb.ext.response.ExtBidPrebid;
import org.prebid.server.proto.openrtb.ext.response.ExtBidPrebidMeta;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

public class AppVideoHtmlCorrectionTest {

    private static final ObjectMapper MAPPER = ObjectMapperProvider.mapper();
    private final AppVideoHtmlCorrection target = new AppVideoHtmlCorrection(MAPPER, 0.1);

    @Test
    public void applyShouldNotChangeBidResponsesFromExcludedBidders() {
        // given
        final Config givenConfig = givenConfig(List.of("bidderA", "bidderB"));
        final List<BidderResponse> givenResponses = List.of(
                BidderResponse.of("bidderA", null, 100),
                BidderResponse.of("bidderB", null, 100));

        // when
        final List<BidderResponse> actual = target.apply(givenConfig, givenResponses);

        // then
        assertThat(actual).isEqualTo(givenResponses);
    }

    private static Config givenConfig(List<String> excludedBidders) {
        return Config.of(true, AppVideoHtmlConfig.of(true, excludedBidders));
    }

    @Test
    public void applyShouldNotChangeBidResponsesWhenAdmIsNull() {
        // given
        final Config givenConfig = givenConfig(List.of("bidderA"));
        final BidderBid givenBid = givenBid(null, BidType.video);

        final List<BidderResponse> givenResponses = List.of(
                BidderResponse.of("bidderA", null, 100),
                BidderResponse.of("bidderB", BidderSeatBid.of(List.of(givenBid)), 100));

        // when
        final List<BidderResponse> actual = target.apply(givenConfig, givenResponses);

        // then
        assertThat(actual).isEqualTo(givenResponses);
    }

    private static BidderBid givenBid(String adm, BidType type) {
        return givenBid(adm, type, null);
    }

    private static BidderBid givenBid(String adm, BidType type, ObjectNode bidExt) {
        final Bid bid = Bid.builder().adm(adm).ext(bidExt).build();
        return BidderBid.of(bid, type, "USD");
    }

    @Test
    public void applyShouldNotChangeBidResponsesWhenBidIsVideoAndHasVastXmlInAdm() {
        // given
        final Config givenConfig = givenConfig(List.of("bidderA"));

        final List<BidderResponse> givenResponses = List.of(
                BidderResponse.of("bidderA", null, 100),
                BidderResponse.of("bidderB", BidderSeatBid.of(
                        List.of(givenBid("<anythingvAstanything", BidType.video))), 100));

        // when
        final List<BidderResponse> actual = target.apply(givenConfig, givenResponses);

        // then
        assertThat(actual).isEqualTo(givenResponses);
    }

    @Test
    public void applyShouldNotChangeBidResponsesWhenBidHasNativeAdm() {
        // given
        final Config givenConfig = givenConfig(List.of("bidderA"));

        final List<BidderResponse> givenResponses = List.of(
                BidderResponse.of("bidderA", null, 100),
                BidderResponse.of("bidderB", BidderSeatBid.of(
                        List.of(givenBid("{\"field\":1,\"assets\":[{\"id\":2}]}", BidType.video))), 100));

        // when
        final List<BidderResponse> actual = target.apply(givenConfig, givenResponses);

        // then
        assertThat(actual).isEqualTo(givenResponses);
    }

    @Test
    public void applyShouldChangeTypeToBannerAndAddMetaTypeVideoWhenAdmIsJsonButNotNative() {
        // given
        final Config givenConfig = givenConfig();

        final List<BidderResponse> givenResponses = List.of(BidderResponse.of(
                "bidderA",
                BidderSeatBid.of(List.of(givenBid("{\"field\":1}", BidType.video))),
                100));

        // when
        final List<BidderResponse> actual = target.apply(givenConfig, givenResponses);

        // then
        final ExtBidPrebid expectedPrebid = ExtBidPrebid.builder()
                .meta(ExtBidPrebidMeta.builder().mediaType("video").build())
                .build();
        final ObjectNode expectedBidExt = MAPPER.valueToTree(ExtPrebid.of(expectedPrebid, null));
        final List<BidderResponse> expectedResponses = List.of(BidderResponse.of(
                "bidderA",
                BidderSeatBid.of(List.of(givenBid("{\"field\":1}", BidType.banner, expectedBidExt))),
                100));

        assertThat(actual).isEqualTo(expectedResponses);
    }

    private static Config givenConfig() {
        return Config.of(true, AppVideoHtmlConfig.of(true, null));
    }

    @Test
    public void applyShouldChangeTypeToBannerAndAddMetaTypeVideoWhenAdmIsVastXmlAndTypeIsNotVideo() {
        // given
        final Config givenConfig = givenConfig();

        final List<BidderResponse> givenResponses = List.of(BidderResponse.of(
                "bidderA",
                BidderSeatBid.of(List.of(givenBid("<something VAST something>", BidType.xNative))),
                100));

        // when
        final List<BidderResponse> actual = target.apply(givenConfig, givenResponses);

        // then
        final ExtBidPrebid expectedPrebid = ExtBidPrebid.builder()
                .meta(ExtBidPrebidMeta.builder().mediaType("video").build())
                .build();
        final ObjectNode expectedBidExt = MAPPER.valueToTree(ExtPrebid.of(expectedPrebid, null));
        final List<BidderResponse> expectedResponses = List.of(BidderResponse.of(
                "bidderA",
                BidderSeatBid.of(List.of(givenBid("<something VAST something>", BidType.banner, expectedBidExt))),
                100));

        assertThat(actual).isEqualTo(expectedResponses);
    }

    @Test
    public void applyShouldChangeTypeToBannerAndOverwriteMetaTypeToVideoWhenAdmIsNotVastXmlAndTypeIsVideo() {
        // given
        final Config givenConfig = givenConfig();

        final ExtBidPrebid givenPrebid = ExtBidPrebid.builder()
                .bidid("someId")
                .meta(ExtBidPrebidMeta.builder().adapterCode("someCode").mediaType("banner").build())
                .build();
        final ObjectNode givenBidExt = MAPPER.valueToTree(ExtPrebid.of(givenPrebid, null));
        final List<BidderResponse> givenResponses = List.of(BidderResponse.of(
                "bidderA",
                BidderSeatBid.of(List.of(givenBid("<something and something>", BidType.video, givenBidExt))),
                100));

        // when
        final List<BidderResponse> actual = target.apply(givenConfig, givenResponses);

        // then
        final ExtBidPrebid expectedPrebid = ExtBidPrebid.builder()
                .bidid("someId")
                .meta(ExtBidPrebidMeta.builder().adapterCode("someCode").mediaType("video").build())
                .build();
        final ObjectNode expectedBidExt = MAPPER.valueToTree(ExtPrebid.of(expectedPrebid, null));
        final List<BidderResponse> expectedResponses = List.of(BidderResponse.of(
                "bidderA",
                BidderSeatBid.of(List.of(givenBid("<something and something>", BidType.banner, expectedBidExt))),
                100));

        assertThat(actual).isEqualTo(expectedResponses);
    }

}
