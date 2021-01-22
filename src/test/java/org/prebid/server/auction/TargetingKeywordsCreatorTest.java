package org.prebid.server.auction;

import org.apache.commons.lang3.StringUtils;
import org.junit.Rule;
import org.junit.Test;
import org.mockito.junit.MockitoJUnit;
import org.mockito.junit.MockitoRule;
import org.prebid.server.proto.openrtb.ext.request.ExtGranularityRange;
import org.prebid.server.proto.openrtb.ext.request.ExtPriceGranularity;
import org.prebid.server.proto.response.Bid;
import org.prebid.server.proto.response.MediaType;

import java.math.BigDecimal;
import java.util.Map;

import static java.util.Collections.singletonList;
import static java.util.Collections.singletonMap;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.entry;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;

public class TargetingKeywordsCreatorTest {

    @Rule
    public final MockitoRule mockitoRule = MockitoJUnit.rule();

    @Test
    public void shouldReturnTargetingKeywordsForOrdinaryBid() {
        // given
        final Bid bid = Bid.builder().bidder("bidder1").price(BigDecimal.ONE).dealId("dealId1")
                .mediaType(MediaType.banner).cacheId("cacheId1")
                .width(50).height(100).build();

        // when
        final Map<String, String> keywords = TargetingKeywordsCreator.create(
                ExtPriceGranularity.of(
                        2,
                        singletonList(ExtGranularityRange.of(BigDecimal.valueOf(5), BigDecimal.valueOf(0.5)))),
                true,
                true,
                false,
                false,
                0,
                null,
                null,
                null)
                .makeFor(bid, false);

        // then
        assertThat(keywords).containsOnly(
                entry("hb_pb_bidder1", "1.00"),
                entry("hb_bidder_bidder1", "bidder1"),
                entry("hb_cache_id_bidder1", "cacheId1"),
                entry("hb_size_bidder1", "50x100"),
                entry("hb_deal_bidder1", "dealId1"));
    }

    @Test
    public void shouldReturnTargetingKeywordsForOrdinaryBidOpenrtb() {
        // given
        final com.iab.openrtb.response.Bid bid = com.iab.openrtb.response.Bid.builder().price(BigDecimal.ONE)
                .dealid("dealId1").w(50).h(100).build();

        // when
        final Map<String, String> keywords = TargetingKeywordsCreator.create(
                ExtPriceGranularity.of(
                        2,
                        singletonList(ExtGranularityRange.of(BigDecimal.valueOf(5), BigDecimal.valueOf(0.5)))),
                true,
                true,
                false,
                false,
                0,
                null,
                null,
                null)
                .makeFor(bid, "bidder1", false, null, null, null);

        // then
        assertThat(keywords).containsOnly(
                entry("hb_pb_bidder1", "1.00"),
                entry("hb_bidder_bidder1", "bidder1"),
                entry("hb_size_bidder1", "50x100"),
                entry("hb_deal_bidder1", "dealId1"));
    }

    @Test
    public void shouldReturnTargetingKeywordsWithEntireKeys() {
        // given
        final Bid bid = Bid.builder().bidder("veryververyverylongbidder1").price(BigDecimal.ONE).dealId("dealId1")
                .cacheId("cacheId1").mediaType(MediaType.banner).width(50).height(100).build();

        // when
        final Map<String, String> keywords = TargetingKeywordsCreator.create(
                ExtPriceGranularity.of(
                        2,
                        singletonList(ExtGranularityRange.of(BigDecimal.valueOf(5), BigDecimal.valueOf(0.5)))),
                true,
                true,
                false,
                false,
                0,
                null,
                null,
                null)
                .makeFor(bid, false);

        // then
        assertThat(keywords).containsOnly(
                entry("hb_pb_veryververyverylongbidder1", "1.00"),
                entry("hb_bidder_veryververyverylongbidder1", "veryververyverylongbidder1"),
                entry("hb_cache_id_veryververyverylongbidder1", "cacheId1"),
                entry("hb_size_veryververyverylongbidder1", "50x100"),
                entry("hb_deal_veryververyverylongbidder1", "dealId1"));
    }

    @Test
    public void shouldReturnTargetingKeywordsWithEntireKeysOpenrtb() {
        // given
        final com.iab.openrtb.response.Bid bid = com.iab.openrtb.response.Bid.builder().price(BigDecimal.ONE)
                .dealid("dealId1").w(50).h(100).build();

        // when
        final Map<String, String> keywords = TargetingKeywordsCreator.create(
                ExtPriceGranularity.of(
                        2,
                        singletonList(ExtGranularityRange.of(BigDecimal.valueOf(5), BigDecimal.valueOf(0.5)))),
                true,
                true,
                false,
                false,
                0,
                null,
                null,
                null)
                .makeFor(bid, "veryververyverylongbidder1", false, null, null, null);

        // then
        assertThat(keywords).containsOnly(
                entry("hb_pb_veryververyverylongbidder1", "1.00"),
                entry("hb_bidder_veryververyverylongbidder1", "veryververyverylongbidder1"),
                entry("hb_size_veryververyverylongbidder1", "50x100"),
                entry("hb_deal_veryververyverylongbidder1", "dealId1"));
    }

    @Test
    public void shouldReturnTargetingKeywordsForWinningBid() {
        // given
        final Bid bid = Bid.builder()
                .bidder("bidder1")
                .price(BigDecimal.ONE)
                .dealId("dealId1")
                .cacheId("cacheId1")
                .mediaType(MediaType.banner).width(50)
                .height(100)
                .build();

        // when
        final Map<String, String> keywords = TargetingKeywordsCreator.create(
                ExtPriceGranularity.of(
                        2,
                        singletonList(ExtGranularityRange.of(BigDecimal.valueOf(5), BigDecimal.valueOf(0.5)))),
                true,
                true,
                true,
                false,
                0,
                null,
                null,
                null)
                .makeFor(bid, true);

        // then
        assertThat(keywords).containsOnly(
                entry("hb_pb_bidder1", "1.00"),
                entry("hb_bidder_bidder1", "bidder1"),
                entry("hb_cache_id_bidder1", "cacheId1"),
                entry("hb_size_bidder1", "50x100"),
                entry("hb_deal_bidder1", "dealId1"),
                entry("hb_pb", "1.00"),
                entry("hb_bidder", "bidder1"),
                entry("hb_cache_id", "cacheId1"),
                entry("hb_size", "50x100"),
                entry("hb_deal", "dealId1"),
                entry("hb_format", "banner"),
                entry("hb_format_bidder1", "banner"));
    }

    @Test
    public void shouldReturnTargetingKeywordsForWinningBidOpenrtb() {
        // given
        final com.iab.openrtb.response.Bid bid = com.iab.openrtb.response.Bid.builder()
                .price(BigDecimal.ONE)
                .dealid("dealId1")
                .w(50)
                .h(100)
                .build();

        // when
        final Map<String, String> keywords = TargetingKeywordsCreator.create(
                ExtPriceGranularity.of(
                        2,
                        singletonList(ExtGranularityRange.of(BigDecimal.valueOf(5), BigDecimal.valueOf(0.5)))),
                true,
                true,
                true,
                false,
                0,
                null,
                null,
                null)
                .makeFor(bid, "bidder1", true, "cacheId1", "banner", "videoCacheId1");

        // then
        assertThat(keywords).containsOnly(
                entry("hb_pb_bidder1", "1.00"),
                entry("hb_bidder_bidder1", "bidder1"),
                entry("hb_size_bidder1", "50x100"),
                entry("hb_deal_bidder1", "dealId1"),
                entry("hb_pb", "1.00"),
                entry("hb_bidder", "bidder1"),
                entry("hb_size", "50x100"),
                entry("hb_deal", "dealId1"),
                entry("hb_cache_id", "cacheId1"),
                entry("hb_cache_id_bidder1", "cacheId1"),
                entry("hb_uuid", "videoCacheId1"),
                entry("hb_uuid_bidder1", "videoCacheId1"),
                entry("hb_format", "banner"),
                entry("hb_format_bidder1", "banner"));
    }

    @Test
    public void shouldFallbackToDefaultPriceIfInvalidPriceGranularity() {
        // given
        final Bid bid = Bid.builder().bidder("").price(BigDecimal.valueOf(3.87)).mediaType(MediaType.banner).build();

        // when
        final Map<String, String> keywords = TargetingKeywordsCreator.create(
                "invalid", true, true, false, false, 0)
                .makeFor(bid, true);

        // then
        assertThat(keywords).contains(entry("hb_pb", StringUtils.EMPTY));
    }

    @Test
    public void shouldIncludeFormat() {
        // given
        final Bid bid = Bid.builder().bidder("").price(BigDecimal.valueOf(3.87)).mediaType(MediaType.banner).build();

        // when
        final Map<String, String> keywords = TargetingKeywordsCreator.create(
                "invalid", true, true, true, false, 0)
                .makeFor(bid, true);

        // then
        assertThat(keywords).contains(entry("hb_format", "banner"));
    }

    @Test
    public void shouldUseDefaultPriceGranularity() {
        // given
        final Bid bid = Bid.builder().bidder("").price(BigDecimal.valueOf(3.87)).mediaType(MediaType.banner).build();

        // when
        final Map<String, String> keywords = TargetingKeywordsCreator.create(null, true, true, false, false, 0)
                .makeFor(bid, true);

        // then
        assertThat(keywords).contains(entry("hb_pb", "3.80"));
    }

    @Test
    public void shouldUseDefaultPriceGranularityOpenrtb() {
        // given
        final com.iab.openrtb.response.Bid bid = com.iab.openrtb.response.Bid.builder()
                .price(BigDecimal.valueOf(3.87)).build();

        // when
        final Map<String, String> keywords = TargetingKeywordsCreator.create(null, true, true, false, false, 0)
                .makeFor(bid, "", true, null, null, null);

        // then
        assertThat(keywords).contains(entry("hb_pb", "3.80"));
    }

    @Test
    public void shouldIncludeFormatOpenrtb() {
        // given
        final com.iab.openrtb.response.Bid bid = com.iab.openrtb.response.Bid.builder()
                .price(BigDecimal.valueOf(3.87)).build();

        // when
        final Map<String, String> keywords = TargetingKeywordsCreator.create(null, true, true, true, false, 0)
                .makeFor(bid, "", true, null, "banner", null);

        // then
        assertThat(keywords).contains(entry("hb_format", "banner"));
    }

    @Test
    public void shouldNotIncludeCacheIdAndDealIdAndSize() {
        // given
        final Bid bid = Bid.builder().bidder("bidder").mediaType(MediaType.banner).price(BigDecimal.ONE).build();

        // when
        final Map<String, String> keywords = TargetingKeywordsCreator.create(null, true, true, false, false, 0)
                .makeFor(bid, true);

        // then
        assertThat(keywords).doesNotContainKeys("hb_cache_id_bidder", "hb_deal_bidder", "hb_size_bidder",
                "hb_cache_id", "hb_deal", "hb_size");
    }

    @Test
    public void shouldNotIncludeCacheIdAndDealIdAndSizeOpenrtb() {
        // given
        final com.iab.openrtb.response.Bid bid = com.iab.openrtb.response.Bid.builder().price(BigDecimal.ONE).build();

        // when
        final Map<String, String> keywords = TargetingKeywordsCreator.create(null, true, true, false, false, 0)
                .makeFor(bid, "bidder", true, null, null, null);

        // then
        assertThat(keywords).doesNotContainKeys("hb_cache_id_bidder", "hb_deal_bidder", "hb_size_bidder",
                "hb_cache_id", "hb_uuid", "hb_deal", "hb_size");
    }

    @Test
    public void shouldReturnEnvKeyForAppRequest() {
        // given
        final Bid bid = Bid.builder().bidder("bidder").mediaType(MediaType.banner).price(BigDecimal.ONE).build();

        // when
        final Map<String, String> keywords = TargetingKeywordsCreator.create(null, true, true, false, true, 0)
                .makeFor(bid, true);

        // then
        assertThat(keywords).contains(
                entry("hb_env", "mobile-app"),
                entry("hb_env_bidder", "mobile-app"));
    }

    @Test
    public void shouldReturnEnvKeyForAppRequestOpenrtb() {
        // given
        final com.iab.openrtb.response.Bid bid = com.iab.openrtb.response.Bid.builder().price(BigDecimal.ONE).build();

        // when
        final Map<String, String> keywords = TargetingKeywordsCreator.create(null, true, true, false, true, 0)
                .makeFor(bid, "bidder", true, null, null, null);

        // then
        assertThat(keywords).contains(
                entry("hb_env", "mobile-app"),
                entry("hb_env_bidder", "mobile-app"));
    }

    @Test
    public void shouldNotIncludeWinningBidTargetingIfIncludeWinnersFlagIsFalse() {
        // given
        final com.iab.openrtb.response.Bid bid = com.iab.openrtb.response.Bid.builder().price(BigDecimal.ONE).build();

        // when
        final Map<String, String> keywords = TargetingKeywordsCreator.create(null, false, true, false, false, 0)
                .makeFor(bid, "bidder1", true, null, null, null);

        // then
        assertThat(keywords).doesNotContainKeys("hb_bidder", "hb_pb");
    }

    @Test
    public void shouldIncludeWinningBidTargetingIfIncludeWinnersFlagIsTrue() {
        // given
        final com.iab.openrtb.response.Bid bid = com.iab.openrtb.response.Bid.builder().price(BigDecimal.ONE).build();

        // when
        final Map<String, String> keywords = TargetingKeywordsCreator.create(null, true, true, false, false, 0)
                .makeFor(bid, "bidder1", true, null, null, null);

        // then
        assertThat(keywords).containsKeys("hb_bidder", "hb_pb");
    }

    @Test
    public void shouldNotIncludeBidderKeysTargetingIfIncludeBidderKeysFlagIsFalse() {
        // given
        final com.iab.openrtb.response.Bid bid = com.iab.openrtb.response.Bid.builder().price(BigDecimal.ONE).build();

        // when
        final Map<String, String> keywords = TargetingKeywordsCreator.create(null, false, false, false, false, 0)
                .makeFor(bid, "bidder1", true, null, null, null);

        // then
        assertThat(keywords).doesNotContainKeys("hb_bidder_bidder1", "hb_pb_bidder1");
    }

    @Test
    public void shouldIncludeBidderKeysTargetingIfIncludeBidderKeysFlagIsTrue() {
        // given
        final com.iab.openrtb.response.Bid bid = com.iab.openrtb.response.Bid.builder().price(BigDecimal.ONE).build();

        // when
        final Map<String, String> keywords = TargetingKeywordsCreator.create(null, false, true, false, false, 0)
                .makeFor(bid, "bidder1", true, null, null, null);

        // then
        assertThat(keywords).containsKeys("hb_bidder_bidder1", "hb_pb_bidder1");
    }

    @Test
    public void shouldTruncateTargetingBidderKeywordsIfTruncateAttrCharsIsDefined() {
        // given
        final com.iab.openrtb.response.Bid bid = com.iab.openrtb.response.Bid.builder().price(BigDecimal.ONE).build();

        // when
        final Map<String, String> keywords = TargetingKeywordsCreator.create(null, false, true, false, false, 20)
                .makeFor(bid, "someVeryLongBidderName", true, null, null, null);

        // then
        assertThat(keywords).hasSize(2)
                .containsKeys("hb_bidder_someVeryLo", "hb_pb_someVeryLongBi");
    }

    @Test
    public void shouldTruncateTargetingWithoutBidderSuffixKeywordsIfTruncateAttrCharsIsDefined() {
        // given
        final com.iab.openrtb.response.Bid bid = com.iab.openrtb.response.Bid.builder().price(BigDecimal.ONE).build();

        // when
        final Map<String, String> keywords = TargetingKeywordsCreator.create(null, true, false, false, false, 7)
                .makeFor(bid, "bidder", true, null, null, null);

        // then
        assertThat(keywords).hasSize(2)
                .containsKeys("hb_bidd", "hb_pb");
    }

    @Test
    public void shouldTruncateTargetingAndDropDuplicatedWhenTruncateIsTooShort() {
        // given
        final com.iab.openrtb.response.Bid bid = com.iab.openrtb.response.Bid.builder().price(BigDecimal.ONE).build();

        // when
        final Map<String, String> keywords = TargetingKeywordsCreator.create(null, true, true, true, true, 6)
                .makeFor(bid, "bidder", true, null, null, null);

        // then
        // Without truncating: "hb_bidder", "hb_bidder_bidder", "hb_env", "hb_env_bidder", "hb_pb", "hb_pb_bidder"
        assertThat(keywords).hasSize(4)
                .containsKeys("hb_bid", "hb_env", "hb_pb", "hb_pb_");
    }

    @Test
    public void shouldNotTruncateTargetingKeywordsIfTruncateAttrCharsIsNotDefined() {
        // given
        final com.iab.openrtb.response.Bid bid = com.iab.openrtb.response.Bid.builder().price(BigDecimal.ONE).build();

        // when
        final Map<String, String> keywords = TargetingKeywordsCreator.create(null, false, true, false, false, 0)
                .makeFor(bid, "someVeryLongBidderName", true, null, null, null);

        // then
        assertThat(keywords).hasSize(2)
                .containsKeys("hb_bidder_someVeryLongBidderName", "hb_pb_someVeryLongBidderName");
    }

    @Test
    public void shouldTruncateKeysFromResolver() {
        // given
        final com.iab.openrtb.response.Bid bid = com.iab.openrtb.response.Bid.builder()
                .id("bid1")
                .price(BigDecimal.ONE)
                .build();

        final TargetingKeywordsResolver resolver = mock(TargetingKeywordsResolver.class);
        given(resolver.resolve(any(), anyString())).willReturn(singletonMap("key_longer_than_twenty", "value1"));

        // when
        final Map<String, String> keywords = TargetingKeywordsCreator.create(
                ExtPriceGranularity.of(
                        2,
                        singletonList(ExtGranularityRange.of(BigDecimal.valueOf(5), BigDecimal.valueOf(0.5)))),
                true,
                true,
                false,
                false,
                20,
                null,
                null,
                resolver)
                .makeFor(bid, "bidder1", true, null, null, null);

        // then
        assertThat(keywords).contains(entry("key_longer_than_twen", "value1"));
    }

    @Test
    public void shouldIncludeKeywordsFromResolver() {
        // given
        final com.iab.openrtb.response.Bid bid = com.iab.openrtb.response.Bid.builder()
                .id("bid1")
                .price(BigDecimal.ONE)
                .build();

        final TargetingKeywordsResolver resolver = mock(TargetingKeywordsResolver.class);
        given(resolver.resolve(any(), anyString())).willReturn(singletonMap("keyword1", "value1"));

        // when
        final Map<String, String> keywords = TargetingKeywordsCreator.create(
                ExtPriceGranularity.of(
                        2,
                        singletonList(ExtGranularityRange.of(BigDecimal.valueOf(5), BigDecimal.valueOf(0.5)))),
                true,
                true,
                false,
                false,
                0,
                null,
                null,
                resolver)
                .makeFor(bid, "bidder1", true, null, null, null);

        // then
        assertThat(keywords).contains(entry("keyword1", "value1"));
    }
}
