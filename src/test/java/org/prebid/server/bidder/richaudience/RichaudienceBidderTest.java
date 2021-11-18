package org.prebid.server.bidder.richaudience;

import com.fasterxml.jackson.databind.node.ObjectNode;
import com.iab.openrtb.request.Banner;
import com.iab.openrtb.request.BidRequest;
import com.iab.openrtb.request.Device;
import com.iab.openrtb.request.Imp;
import com.iab.openrtb.request.Site;
import org.junit.Before;
import org.junit.Test;
import org.prebid.server.VertxTest;
import org.prebid.server.bidder.model.BidderError;
import org.prebid.server.bidder.model.HttpRequest;
import org.prebid.server.bidder.model.Result;
import org.prebid.server.proto.openrtb.ext.ExtPrebid;
import org.prebid.server.proto.openrtb.ext.request.richaudience.ExtImpRichaudience;
import org.prebid.server.proto.openrtb.ext.request.richaudience.ExtImpRichaudience.ExtImpRichaudienceBuilder;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.function.Function;
import java.util.function.UnaryOperator;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;
import static org.assertj.core.groups.Tuple.tuple;

public class RichaudienceBidderTest extends VertxTest {

    private RichaudienceBidder bidder;

    @Before
    public void setUp() {
        bidder = new RichaudienceBidder(ENDPOINT_URL, jacksonMapper);
    }

    @Test
    public void creationShouldFailOnInvalidEndpointUrl() {
        assertThatIllegalArgumentException().isThrownBy(() -> new RichaudienceBidder(INCORRECT_URL, jacksonMapper));
    }

    @Test
    public void makeHttpRequestsShouldReturnErrorIfDeviceAbsent() {
        // given
        final BidRequest bidRequest = givenBidRequest(BidRequestCustomizer.identity());

        // when
        final Result<List<HttpRequest<BidRequest>>> result = bidder.makeHttpRequests(bidRequest);

        // then
        assertThat(result.getValue()).isEmpty();
        assertThat(result.getErrors()).hasSize(1).containsExactly(BidderError.badInput(DEVICE_ERROR));
    }

    @Test
    public void makeHttpRequestsShouldReturnErrorIfDeviceIpAbsent() {
        // given
        final BidRequest bidRequest = givenBidRequest(withEmptyDevice);

        // when
        final Result<List<HttpRequest<BidRequest>>> result = bidder.makeHttpRequests(bidRequest);

        // then
        assertThat(result.getValue()).isEmpty();
        assertThat(result.getErrors()).hasSize(1).containsExactly(BidderError.badInput(DEVICE_ERROR));
    }

    @Test
    public void makeHttpRequestsShouldReturnErrorIfSiteAbsent() {
        // given
        final BidRequest bidRequest = givenBidRequest(withDevice);

        // when
        final Result<List<HttpRequest<BidRequest>>> result = bidder.makeHttpRequests(bidRequest);

        // then
        assertThat(result.getValue()).isEmpty();
        assertThat(result.getErrors()).hasSize(1).containsExactly(BidderError.badInput(URL_ERROR + "null"));
    }

    @Test
    public void makeHttpRequestsShouldReturnErrorIfSitePageAbsent() {
        // given
        final BidRequest bidRequest = givenBidRequest(withDevice.andThen(withEmptySite));

        // when
        final Result<List<HttpRequest<BidRequest>>> result = bidder.makeHttpRequests(bidRequest);

        // then
        assertThat(result.getValue()).isEmpty();
        assertThat(result.getErrors()).hasSize(1).containsExactly(BidderError.badInput(URL_ERROR + "null"));
    }

    @Test
    public void makeHttpRequestsShouldReturnErrorIfSitePageIncorrect() {
        // given
        final BidRequest bidRequest = givenBidRequest(withDevice.andThen(withIncorrectSite));

        // when
        final Result<List<HttpRequest<BidRequest>>> result = bidder.makeHttpRequests(bidRequest);

        // then
        assertThat(result.getValue()).isEmpty();
        assertThat(result.getErrors()).hasSize(1).containsExactly(BidderError.badInput(URL_ERROR + INCORRECT_URL));
    }

    @Test
    public void makeHttpRequestsIfSomeImpHaveIncorrectBanner() {
        // given
        final BidRequest bidRequest =
                givenBidRequest(withDevice
                        .andThen(withSecureSite)
                        .andThen(withEmptyImp)
                        .andThen(withImpWithEmptyBanner)
                        .andThen(withImpWithExt));

        // when
        final Result<List<HttpRequest<BidRequest>>> result = bidder.makeHttpRequests(bidRequest);

        // then
        assertThat(result.getValue()).hasSize(1);
        assertThat(result.getValue().get(0).getPayload().getImp()).hasSize(1);
        assertThat(result.getErrors()).hasSize(2).allMatch(BidderError.badInput(BANNER_ERROR)::equals);
    }

    @Test
    public void makeHttpRequestsIfSomeImpHaveIncorrectExt() {
        // given
        final BidRequest bidRequest =
                givenBidRequest(withDevice
                        .andThen(withSecureSite)
                        .andThen(withImpWithoutExt)
                        .andThen(withImpWithExt));

        // when
        final Result<List<HttpRequest<BidRequest>>> result = bidder.makeHttpRequests(bidRequest);

        // then
        assertThat(result.getValue()).hasSize(1);
        assertThat(result.getValue().get(0).getPayload().getImp()).hasSize(1);
        assertThat(result.getErrors()).hasSize(1).containsExactly(BidderError.badInput(EXT_ERROR));
    }

    @Test
    public void makeHttpRequestsIfSomeImpExtBidFloorCurAbsent() {
        final BidRequest bidRequest =
                givenBidRequest(withDevice
                        .andThen(withSecureSite)
                        .andThen(withImpWithEmptyExt)
                        .andThen(withImpWithExt));

        // when
        final Result<List<HttpRequest<BidRequest>>> result = bidder.makeHttpRequests(bidRequest);

        // then
        assertThat(result.getValue()).hasSize(1);
        assertThat(result.getValue().get(0).getPayload().getImp())
                .hasSize(2)
                .extracting(Imp::getBidfloorcur)
                .containsExactly(DEFAULT_CUR, CUR);
        assertThat(result.getErrors()).isEmpty();
    }

    @Test
    public void makeHttpRequestsIfSitePageUnsecure() {
        final BidRequest bidRequest =
                givenBidRequest(withDevice
                        .andThen(withUnsecureSite)
                        .andThen(withImpWithEmptyExt)
                        .andThen(withImpWithExt));

        // when
        final Result<List<HttpRequest<BidRequest>>> result = bidder.makeHttpRequests(bidRequest);

        // then
        assertThat(result.getValue()).hasSize(1);
        assertThat(result.getValue().get(0).getPayload().getImp())
                .hasSize(2)
                .extracting(Imp::getSecure)
                .allMatch(Integer.valueOf(0)::equals);
        assertThat(result.getErrors()).isEmpty();
    }

    @Test
    public void makeHttpRequestsIfSomeImpExtTestIsTrue() {
        final BidRequest bidRequest =
                givenBidRequest(withDevice
                        .andThen(withSecureSite)
                        .andThen(withImpWithEmptyExt)
                        .andThen(withImpWithExtWithTest)
                        .andThen(withImpWithExt));

        // when
        final Result<List<HttpRequest<BidRequest>>> result = bidder.makeHttpRequests(bidRequest);

        // then
        assertThat(result.getValue()).hasSize(1);
        assertThat(result.getValue().get(0).getPayload().getTest()).isEqualTo(1);
        assertThat(result.getErrors()).isEmpty();
    }

    @Test
    public void makeHttpRequests() {
        final BidRequest bidRequest =
                givenBidRequest(withDevice
                        .andThen(withSecureSite)
                        .andThen(withImpWithEmptyExt)
                        .andThen(withImpWithExt));

        // when
        final Result<List<HttpRequest<BidRequest>>> result = bidder.makeHttpRequests(bidRequest);

        // then
        assertThat(result.getValue()).hasSize(1);
        assertThat(result.getValue().get(0).getPayload().getImp())
                .hasSize(2)
                .extracting(Imp::getTagid, Imp::getBidfloorcur, Imp::getSecure)
                .containsExactly(tuple(null, DEFAULT_CUR, 1), tuple(PID, CUR, 1));
        assertThat(result.getErrors()).isEmpty();
    }

    private static final String ENDPOINT_URL = "http://test.domain.dm/uri";
    private static final String INCORRECT_URL = "incorrect_url";
    private static final String DEVICE_IP = "device_ip";
    private static final String UNSECURE_SITE_URL = "http://site.st/uri";
    private static final String SECURE_SITE_URL = "https://site.st/uri";
    private static final Integer W = 21;
    private static final Integer H = 9;
    private static final String PID = "123";
    private static final String CUR = "UAH";
    private static final String DEFAULT_CUR = "USD";

    private static final String DEVICE_ERROR = "Device IP is required.";
    private static final String URL_ERROR = "Problem with Request.Site: URL supplied is not valid: ";
    private static final String BANNER_ERROR = "Banner W/H/Format is required. ImpId: null";
    private static final String EXT_ERROR = "Ext not found. ImpId: null";

    private BidRequest givenBidRequest(BidRequestCustomizer bidRequestCustomizer) {
        return bidRequestCustomizer.apply(BidRequest.builder()).build();
    }

    private final BidRequestCustomizer withEmptyDevice = withDevice(null);
    private final BidRequestCustomizer withDevice = withDevice(DEVICE_IP);

    private final BidRequestCustomizer withEmptySite = withSite(null);
    private final BidRequestCustomizer withIncorrectSite = withSite(INCORRECT_URL);
    private final BidRequestCustomizer withUnsecureSite = withSite(UNSECURE_SITE_URL);
    private final BidRequestCustomizer withSecureSite = withSite(SECURE_SITE_URL);

    private final BidRequestCustomizer withEmptyImp = withImp(UnaryOperator.identity());
    private final BidRequestCustomizer withImpWithEmptyBanner = withImp(withBanner(UnaryOperator.identity()));
    private final BidRequestCustomizer withImpWithoutExt = withImp(withBanner());
    private final BidRequestCustomizer withImpWithEmptyExt =
            withImp(withBanner().andThen(withExt(UnaryOperator.identity())));
    private final BidRequestCustomizer withImpWithExtWithTest =
            withImp(withBanner().andThen(withExt(extCustomizer -> extCustomizer.test(true))));
    private final BidRequestCustomizer withImpWithExt =
            withImp(withBanner().andThen(withExt(extCustomizer -> extCustomizer.pid(PID).bidFloorCur(CUR))));

    private BidRequestCustomizer withDevice(String ip) {
        return customizer -> customizer.device(Device.builder().ip(ip).build());
    }

    private BidRequestCustomizer withSite(String url) {
        return customizer -> customizer.site(Site.builder().page(url).build());
    }

    private BidRequestCustomizer withImp(Function<Imp.ImpBuilder, Imp.ImpBuilder> impCustomizer) {
        return customizer -> {
            final BidRequest built = customizer.build();
            final List<Imp> newImps = built.getImp() != null
                    ? built.getImp()
                    : new ArrayList<>();
            newImps.add(impCustomizer.apply(Imp.builder()).build());
            return built.toBuilder().imp(newImps);
        };
    }

    private UnaryOperator<Imp.ImpBuilder> withBanner(UnaryOperator<Banner.BannerBuilder> bannerCustomizer) {
        return customizer -> customizer.banner(bannerCustomizer.apply(Banner.builder()).build());
    }

    private UnaryOperator<Imp.ImpBuilder> withBanner() {
        return withBanner(bannerCustomizer -> bannerCustomizer.w(W).h(H));
    }

    private UnaryOperator<Imp.ImpBuilder> withExt(UnaryOperator<ExtImpRichaudienceBuilder> extCustomizer) {
        final ExtImpRichaudience extImpRichaudience = extCustomizer.apply(ExtImpRichaudience.builder()).build();
        final ObjectNode ext = mapper.valueToTree(ExtPrebid.of(null, extImpRichaudience));
        return customizer -> customizer.ext(ext);
    }

    private interface BidRequestCustomizer extends UnaryOperator<BidRequest.BidRequestBuilder> {
        static BidRequestCustomizer identity() {
            return t -> t;
        }

        default BidRequestCustomizer andThen(BidRequestCustomizer after) {
            Objects.requireNonNull(after);
            return t -> after.apply(apply(t));
        }
    }
}
