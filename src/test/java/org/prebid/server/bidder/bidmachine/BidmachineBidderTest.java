package org.prebid.server.bidder.bidmachine;

import com.iab.openrtb.request.Banner;
import com.iab.openrtb.request.BidRequest;
import com.iab.openrtb.request.Device;
import com.iab.openrtb.request.Format;
import com.iab.openrtb.request.Imp;
import com.iab.openrtb.request.Site;
import com.iab.openrtb.request.Video;
import org.junit.Before;
import org.junit.Test;
import org.prebid.server.VertxTest;
import org.prebid.server.bidder.model.BidderError;
import org.prebid.server.bidder.model.HttpRequest;
import org.prebid.server.bidder.model.Result;
import org.prebid.server.proto.openrtb.ext.ExtPrebid;
import org.prebid.server.proto.openrtb.ext.request.ExtImpPrebid;
import org.prebid.server.proto.openrtb.ext.request.bidmachine.ExtImpBidmachine;
import org.prebid.server.util.HttpUtil;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

import static java.util.Collections.singletonList;
import static java.util.function.Function.identity;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;
import static org.assertj.core.api.Assertions.tuple;

public class BidmachineBidderTest extends VertxTest {

    private static final String ENDPOINT_URL = "https://{{HOST}}/{{PATH}}/{{SELLER_ID}}";

    private BidmachineBidder bidmachineBidder;

    @Before
    public void setUp() {
        bidmachineBidder = new BidmachineBidder(ENDPOINT_URL, jacksonMapper);
    }

    @Test
    public void creationShouldFailOnInvalidEndpointUrl() {
        assertThatIllegalArgumentException().isThrownBy(() -> new BidmachineBidder("invalid_url", jacksonMapper));
    }

    @Test
    public void makeHttpRequestsShouldReturnErrorIfImpExtContainEmptyOrNullHostParam() {
        // given
        final Imp firstImp = givenImp(impBuilder -> impBuilder
                .ext(mapper.valueToTree(ExtPrebid.of(null, ExtImpBidmachine.of("", "pubId", "1")))));

        final Imp secondImp = givenImp(impBuilder -> impBuilder
                .id("456")
                .ext(mapper.valueToTree(ExtPrebid.of(null, ExtImpBidmachine.of(null, "pubId", "1")))));

        final BidRequest bidRequest = BidRequest.builder()
                .imp(Arrays.asList(firstImp, secondImp))
                .build();

        // when
        final Result<List<HttpRequest<BidRequest>>> result = bidmachineBidder.makeHttpRequests(bidRequest);

        // then
        assertThat(result.getErrors()).hasSize(2)
                .containsExactly(
                        BidderError.badInput("Invalid/Missing host"),
                        BidderError.badInput("Invalid/Missing host"));
    }

    @Test
    public void makeHttpRequestsShouldCorrectlyAddHeaders() {
        // given
        final Imp firstImp = givenImp(impBuilder -> impBuilder
                .ext(mapper.valueToTree(ExtPrebid.of(null,
                        ExtImpBidmachine.of("host", "pubId", "1")))));

        final BidRequest bidRequest = BidRequest.builder()
                .device(Device.builder().ua("someUa").dnt(5).ip("someIp").language("someLanguage").build())
                .site(Site.builder().page("somePage").build())
                .imp(singletonList(firstImp))
                .build();

        // when
        final Result<List<HttpRequest<BidRequest>>> result = bidmachineBidder.makeHttpRequests(bidRequest);

        // then
        assertThat(result.getValue())
                .flatExtracting(res -> res.getHeaders().entries())
                .extracting(Map.Entry::getKey, Map.Entry::getValue)
                .contains(
                        tuple(HttpUtil.X_OPENRTB_VERSION_HEADER.toString(), "2.5"));
    }

    @Test
    public void makeHttpRequestsShouldReturnErrorIfImpExtContainEmptyOrNullPath() {
        // given
        final Imp firstImp = givenImp(impBuilder -> impBuilder
                .ext(mapper.valueToTree(ExtPrebid.of(null, ExtImpBidmachine.of("host", "", "2")))));

        final Imp secondImp = givenImp(impBuilder -> impBuilder
                .id("456")
                .ext(mapper.valueToTree(ExtPrebid.of(null, ExtImpBidmachine.of("host", null, "3")))));

        final BidRequest bidRequest = BidRequest.builder()
                .imp(Arrays.asList(firstImp, secondImp))
                .build();

        // when
        final Result<List<HttpRequest<BidRequest>>> result = bidmachineBidder.makeHttpRequests(bidRequest);

        // then
        assertThat(result.getErrors()).hasSize(2)
                .containsExactlyInAnyOrder(
                        BidderError.badInput("Invalid/Missing path"),
                        BidderError.badInput("Invalid/Missing path"));
    }

    @Test
    public void makeHttpRequestsShouldReturnErrorIfImpExtContainEmptyOrNullSellerId() {
        // given
        final Imp firstImp = givenImp(impBuilder -> impBuilder
                .ext(mapper.valueToTree(ExtPrebid.of(null, ExtImpBidmachine.of("host", "path", "")))));

        final Imp secondImp = givenImp(impBuilder -> impBuilder
                .id("456")
                .ext(mapper.valueToTree(ExtPrebid.of(null, ExtImpBidmachine.of("host", "path", null)))));

        final BidRequest bidRequest = BidRequest.builder()
                .imp(Arrays.asList(firstImp, secondImp))
                .build();

        // when
        final Result<List<HttpRequest<BidRequest>>> result = bidmachineBidder.makeHttpRequests(bidRequest);

        // then
        assertThat(result.getErrors()).hasSize(2)
                .containsExactlyInAnyOrder(
                        BidderError.badInput("Invalid/Missing sellerId"),
                        BidderError.badInput("Invalid/Missing sellerId"));
    }

    @Test
    public void makeHttpRequestsShouldReturnErrorsOfNotValidImps() {
        // given
        final BidRequest bidRequest = givenBidRequest(
                impBuilder -> impBuilder
                        .ext(mapper.valueToTree(ExtPrebid.of(null, mapper.createArrayNode()))));
        // when
        final Result<List<HttpRequest<BidRequest>>> result = bidmachineBidder.makeHttpRequests(bidRequest);

        // then
        assertThat(result.getErrors()).hasSize(1);
        ;
    }

    @Test
    public void makeHttpRequestsShouldCreateCorrectURL() {
        // given
        final BidRequest bidRequest = givenBidRequest(
                impBuilder -> impBuilder.banner(Banner.builder()
                        .format(singletonList(Format.builder().w(300).h(500).build()))
                        .build()));

        // when
        final Result<List<HttpRequest<BidRequest>>> result = bidmachineBidder.makeHttpRequests(bidRequest);

        // then
        assertThat(result.getErrors()).isEmpty();
        assertThat(result.getValue()).hasSize(1);
        assertThat(result.getValue())
                .extracting(HttpRequest::getUri)
                .containsExactly("https://127.0.0.1/path/1");
    }

    @Test
    public void makeHttpRequestsShouldNotModifyImplIfNoPrebidIsRequest() {
        // given
        final BidRequest bidRequest = givenBidRequest(
                impBuilder -> impBuilder.banner(Banner.builder()
                        .format(singletonList(Format.builder().w(300).h(500).build()))
                        .build()));

        // when
        final Result<List<HttpRequest<BidRequest>>> result = bidmachineBidder.makeHttpRequests(bidRequest);

        // then
        assertThat(result.getErrors()).isEmpty();
        assertThat(result.getValue()).hasSize(1);
        assertThat(result.getValue())
                .extracting(HttpRequest::getPayload)
                .containsExactly(bidRequest);
    }

    @Test
    public void makeHttpRequestsShouldModifyImplIfPrebidIsRequestAndBannerBattrDoesNotContain16() {
        // given
        List<Integer> requestBattr = new ArrayList<>();
        requestBattr.add(1);
        final Imp imp = givenImp(impBuilder -> impBuilder
                .banner(Banner.builder()
                        .format(singletonList(Format.builder().w(300).h(500).build()))
                        .battr(requestBattr).build())
                .ext(mapper.valueToTree(ExtPrebid.of(
                        ExtImpPrebid.builder()
                                .isRewardedInventory(1)
                                .build(), ExtImpBidmachine.of("host", "pubId", "1")))));
        final BidRequest bidRequest = BidRequest.builder()
                .imp(singletonList(imp))
                .build();
        // when
        final Result<List<HttpRequest<BidRequest>>> result = bidmachineBidder.makeHttpRequests(bidRequest);

        // then
        assertThat(result.getErrors()).isEmpty();
        assertThat(result.getValue()).hasSize(1);
        assertThat(result.getValue())
                .extracting(HttpRequest::getPayload)
                .extracting(BidRequest::getImp)
                .extracting(imps -> imps.get(0))
                .extracting(currImp -> currImp.getBanner().getBattr())
                .containsExactly(Arrays.asList(1, 16));
    }

    @Test
    public void makeHttpRequestsShouldModifyImplIfPrebidIsRequestAndVideoBattrDoesNotContain16() {
        // given
        List<Integer> requestBattr = new ArrayList<>();
        requestBattr.add(1);
        final Imp imp = givenImp(impBuilder -> impBuilder
                .video(Video.builder().battr(requestBattr).build())
                .ext(mapper.valueToTree(ExtPrebid.of(
                        ExtImpPrebid.builder()
                                .isRewardedInventory(1)
                                .build(), ExtImpBidmachine.of("host", "pubId", "1")))));
        final BidRequest bidRequest = BidRequest.builder()
                .imp(singletonList(imp))
                .build();
        // when
        final Result<List<HttpRequest<BidRequest>>> result = bidmachineBidder.makeHttpRequests(bidRequest);

        // then
        assertThat(result.getErrors()).isEmpty();
        assertThat(result.getValue()).hasSize(1);
        assertThat(result.getValue())
                .extracting(HttpRequest::getPayload)
                .extracting(BidRequest::getImp)
                .extracting(imps -> imps.get(0))
                .extracting(currImp -> currImp.getVideo().getBattr())
                .containsExactly(Arrays.asList(1, 16));
    }

    private static Imp givenImp(Function<Imp.ImpBuilder, Imp.ImpBuilder> impCustomizer) {
        return impCustomizer.apply(Imp.builder()
                .id("123")
                .banner(Banner.builder().w(23).h(25).build())
                .ext(mapper.valueToTree(ExtPrebid.of(null, ExtImpBidmachine.of("127.0.0.1", "path", "1")))))
                .build();
    }

    private static BidRequest givenBidRequest(Function<Imp.ImpBuilder, Imp.ImpBuilder> impCustomizer) {
        return givenBidRequest(identity(), impCustomizer);
    }

    private static BidRequest givenBidRequest(
            Function<BidRequest.BidRequestBuilder, BidRequest.BidRequestBuilder> bidRequestCustomizer,
            Function<Imp.ImpBuilder, Imp.ImpBuilder> impCustomizer) {

        return bidRequestCustomizer.apply(BidRequest.builder()
                .imp(singletonList(givenImp(impCustomizer))))
                .build();
    }


}