package org.prebid.server.bidder.richaudience;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.iab.openrtb.request.Banner;
import com.iab.openrtb.request.BidRequest;
import com.iab.openrtb.request.BidRequest.BidRequestBuilder;
import com.iab.openrtb.request.Device;
import com.iab.openrtb.request.Imp;
import com.iab.openrtb.request.Site;
import org.junit.Before;
import org.junit.Test;
import org.prebid.server.VertxTest;
import org.prebid.server.bidder.model.BidderBid;
import org.prebid.server.bidder.model.BidderError;
import org.prebid.server.bidder.model.HttpCall;
import org.prebid.server.bidder.model.HttpRequest;
import org.prebid.server.bidder.model.HttpResponse;
import org.prebid.server.bidder.model.Result;
import org.prebid.server.proto.openrtb.ext.ExtPrebid;
import org.prebid.server.proto.openrtb.ext.request.richaudience.ExtImpRichaudience;
import org.prebid.server.proto.openrtb.ext.request.richaudience.ExtImpRichaudience.ExtImpRichaudienceBuilder;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.function.Function;
import java.util.function.UnaryOperator;

import static java.util.function.UnaryOperator.identity;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

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
        final BidRequest bidRequest = givenBidRequest(identity());

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
    public void makeHttpRequestsShouldReturnErrorIfSomeImpHaveIncorrectBanner() {
        // given
        final BidRequest bidRequest =
                givenBidRequest(withDevice
                        .andThen(withSecureSite)
                        .andThen(withEmptyImp)
                        .andThen(withImpWithEmptyBanner)
                        .andThen(withImpWithExt)); // success

        // when
        final Result<List<HttpRequest<BidRequest>>> result = bidder.makeHttpRequests(bidRequest);

        // then
        assertThat(result.getValue()).hasSize(1);
        assertThat(result.getValue().get(0).getPayload().getImp()).hasSize(1);
        assertThat(result.getErrors()).hasSize(2).allMatch(BidderError.badInput(BANNER_ERROR)::equals);
    }

    @Test
    public void makeHttpRequestsShouldSetImpBidFloorCurIfImpExtBidFloorCurExistsOrDefault() {
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
    public void makeHttpRequestsShouldSetImpSecureToFalseIfSitePageUnsecure() {
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
    public void makeHttpRequestsShouldSetImpSecureToTrueIfSitePageSecure() {
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
                .extracting(Imp::getSecure)
                .allMatch(Integer.valueOf(1)::equals);
        assertThat(result.getErrors()).isEmpty();
    }

    @Test
    public void makeHttpRequestsShouldSetTestToTrueIfSomeImpExtTestIsTrue() {
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
    public void makeHttpRequestsShouldSetImpTagIdIfImpExtPidExists() {
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
                .extracting(Imp::getTagid)
                .containsExactly(null, PID);
        assertThat(result.getErrors()).isEmpty();
    }

    @Test
    public void makeBidsShouldReturnErrorIfResponseBodyCouldNotBeParsed() {
        // given
        final HttpCall<BidRequest> httpCall = givenHttpCall(INCORRECT_BODY);

        // when
        final Result<List<BidderBid>> result = bidder.makeBids(httpCall, null);

        // then
        assertThat(result.getValue()).isEmpty();
        assertThat(result.getErrors())
                .hasSize(1)
                .allMatch(error -> error.getType().equals(BidderError.Type.bad_server_response))
                .allMatch(error -> error.getMessage().startsWith(DECODE_ERROR));
    }

    @Test
    public void makeBidsShouldReturnEmptyListIfBidResponseIsNull() throws JsonProcessingException {
        // given
        final HttpCall<BidRequest> httpCall = givenHttpCall(EMPTY_BODY);

        // when
        final Result<List<BidderBid>> result = bidder.makeBids(httpCall, null);

        // then
        assertThat(result.getErrors()).isEmpty();
        assertThat(result.getValue()).isEmpty();
    }

    @Test
    public void makeBidsShouldReturnEmptyListIfBidResponseSeatBidIsNull() throws JsonProcessingException {
        // given
        final HttpCall<BidRequest> httpCall = givenHttpCall(EMPTY_OBJECT);

        // when
        final Result<List<BidderBid>> result = bidder.makeBids(httpCall, null);

        // then
        assertThat(result.getErrors()).isEmpty();
        assertThat(result.getValue()).isEmpty();
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

    private static final String INCORRECT_BODY = "Incorrect body";
    private static final String EMPTY_BODY = "null";
    private static final String EMPTY_OBJECT = "{}";

    private static final String DECODE_ERROR = "Failed to decode: Unrecognized token";

    private BidRequest givenBidRequest(Function<BidRequestBuilder, BidRequestBuilder> bidRequestCustomizer) {
        return bidRequestCustomizer.apply(BidRequest.builder()).build();
    }

    private HttpCall<BidRequest> givenHttpCall(String body) {
        final HttpResponse response = HttpResponse.of(200, null, body);
        return HttpCall.success(null, response, null);
    }

    private final BidRequestCustomizer withEmptyDevice = withDevice(null);
    private final BidRequestCustomizer withDevice = withDevice(DEVICE_IP);

    private final BidRequestCustomizer withEmptySite = withSite(null);
    private final BidRequestCustomizer withIncorrectSite = withSite(INCORRECT_URL);
    private final BidRequestCustomizer withUnsecureSite = withSite(UNSECURE_SITE_URL);
    private final BidRequestCustomizer withSecureSite = withSite(SECURE_SITE_URL);

    private final BidRequestCustomizer withEmptyImp = withImp(identity());
    private final BidRequestCustomizer withImpWithEmptyBanner = withImp(withBanner(identity()));
    private final BidRequestCustomizer withImpWithEmptyExt = withImpWithExt(identity());
    private final BidRequestCustomizer withImpWithExtWithTest = withImpWithExt(c -> c.test(true));
    private final BidRequestCustomizer withImpWithExt = withImpWithExt(c -> c.pid(PID).bidFloorCur(CUR));

    private BidRequestCustomizer withDevice(String ip) {
        return customizer -> customizer.device(Device.builder().ip(ip).build());
    }

    private BidRequestCustomizer withSite(String url) {
        return customizer -> customizer.site(Site.builder().page(url).build());
    }

    private BidRequestCustomizer withImp(Function<Imp.ImpBuilder, Imp.ImpBuilder> impCustomizer) {
        return customizer -> {
            final BidRequest built = customizer.build();
            final List<Imp> newImps = Objects.requireNonNullElseGet(built.getImp(), ArrayList::new);
            newImps.add(impCustomizer.apply(Imp.builder()).build());
            return built.toBuilder().imp(newImps);
        };
    }

    private BidRequestCustomizer withImpWithExt(UnaryOperator<ExtImpRichaudienceBuilder> extCustomizer) {
        final ExtImpRichaudience extImpRichaudience = extCustomizer.apply(ExtImpRichaudience.builder()).build();
        final ObjectNode ext = mapper.valueToTree(ExtPrebid.of(null, extImpRichaudience));
        return withImp(withBanner().andThen(customizer -> customizer.ext(ext)));
    }

    private UnaryOperator<Imp.ImpBuilder> withBanner(UnaryOperator<Banner.BannerBuilder> bannerCustomizer) {
        return customizer -> customizer.banner(bannerCustomizer.apply(Banner.builder()).build());
    }

    private UnaryOperator<Imp.ImpBuilder> withBanner() {
        return withBanner(bannerCustomizer -> bannerCustomizer.w(W).h(H));
    }

    private interface BidRequestCustomizer extends UnaryOperator<BidRequestBuilder> {
    }
}
