package org.prebid.server.bidder.flipp;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.iab.openrtb.request.Banner;
import com.iab.openrtb.request.BidRequest;
import com.iab.openrtb.request.Device;
import com.iab.openrtb.request.Format;
import com.iab.openrtb.request.Imp;
import com.iab.openrtb.request.Site;
import com.iab.openrtb.request.User;
import com.iab.openrtb.response.Bid;
import io.netty.handler.codec.http.HttpHeaderValues;
import io.vertx.core.MultiMap;
import org.junit.Test;
import org.prebid.server.VertxTest;
import org.prebid.server.bidder.flipp.model.request.CampaignRequestBody;
import org.prebid.server.bidder.flipp.model.request.CampaignRequestBodyUser;
import org.prebid.server.bidder.flipp.model.request.Placement;
import org.prebid.server.bidder.flipp.model.request.PrebidRequest;
import org.prebid.server.bidder.flipp.model.request.Properties;
import org.prebid.server.bidder.flipp.model.response.CampaignResponseBody;
import org.prebid.server.bidder.flipp.model.response.Content;
import org.prebid.server.bidder.flipp.model.response.Data;
import org.prebid.server.bidder.flipp.model.response.Decisions;
import org.prebid.server.bidder.flipp.model.response.Inline;
import org.prebid.server.bidder.flipp.model.response.Prebid;
import org.prebid.server.bidder.model.BidderBid;
import org.prebid.server.bidder.model.BidderCall;
import org.prebid.server.bidder.model.BidderError;
import org.prebid.server.bidder.model.HttpRequest;
import org.prebid.server.bidder.model.HttpResponse;
import org.prebid.server.bidder.model.Result;
import org.prebid.server.proto.openrtb.ext.ExtPrebid;
import org.prebid.server.proto.openrtb.ext.request.flipp.ExtImpFlipp;
import org.prebid.server.proto.openrtb.ext.request.flipp.ExtImpFlippOptions;
import org.prebid.server.util.HttpUtil;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.UnaryOperator;

import static java.util.Collections.singletonList;
import static java.util.function.UnaryOperator.identity;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;
import static org.assertj.core.api.Assertions.tuple;
import static org.prebid.server.proto.openrtb.ext.response.BidType.banner;

public class FlippBidderTest extends VertxTest {

    private static final String ENDPOINT_URL = "https://test.endpoint.com";

    private final FlippBidder target = new FlippBidder(ENDPOINT_URL, jacksonMapper);

    @Test
    public void creationShouldFailOnInvalidEndpointUrl() {
        assertThatIllegalArgumentException().isThrownBy(() -> new FlippBidder("invalid_url", jacksonMapper));
    }

    @Test
    public void makeHttpRequestsShouldReturnValidResponse() {
        // given
        final BidRequest bidRequest = givenBidRequest(identity());

        // when
        final Result<List<HttpRequest<CampaignRequestBody>>> result = target.makeHttpRequests(bidRequest);

        // then
        assertThat(result.getErrors()).isEmpty();
        assertThat(result.getValue()).hasSize(1);
    }

    @Test
    public void makeHttpRequestsShouldReturnErrorWhenExtPrebidInvalid() {
        // given
        final BidRequest bidRequest = givenBidRequest(identity(),
                impBuilder -> impBuilder.ext(mapper.valueToTree(ExtPrebid.of(null, "any"))));

        // when
        final Result<List<HttpRequest<CampaignRequestBody>>> result = target.makeHttpRequests(bidRequest);

        // then
        assertThat(result.getValue()).isEmpty();
        assertThat(result.getErrors()).hasSize(2)
                .anySatisfy(error -> {
                    assertThat(error.getType()).isEqualTo(BidderError.Type.bad_input);
                    assertThat(error.getMessage()).startsWith("Flipp params not found.");
                });
    }

    @Test
    public void makeHttpRequestsShouldReturnErrorWhenDeviceNotContainId() {
        // given
        final BidRequest bidRequest = givenBidRequest(
                bidRequestBuilder -> bidRequestBuilder.device(Device.builder().ip(null).build()), identity());

        // when
        final Result<List<HttpRequest<CampaignRequestBody>>> result = target.makeHttpRequests(bidRequest);

        // then
        assertThat(result.getValue()).isEmpty();
        assertThat(result.getErrors()).hasSize(2)
                .containsExactlyInAnyOrder(
                        BidderError.badInput("No IP set in Flipp bidder params or request device"),
                        BidderError.badInput("Adapter request is empty"));
    }

    @Test
    public void makeHttpRequestsShouldCorrectlyPopulateAdTypesWhenCreativeTypeIsDTX() {
        // given
        final BidRequest bidRequest = givenBidRequest(impBuilder -> impBuilder.ext(
                mapper.valueToTree(ExtPrebid.of(null, ExtImpFlipp.builder()
                        .publisherNameIdentifier("publisherName")
                        .creativeType("DTX")
                        .zoneIds(List.of(12))
                        .build()))));

        // when
        final Result<List<HttpRequest<CampaignRequestBody>>> result = target.makeHttpRequests(bidRequest);

        // then
        assertThat(result.getErrors()).isEmpty();
        assertThat(result.getValue())
                .extracting(HttpRequest::getPayload)
                .flatExtracting(CampaignRequestBody::getPlacements)
                .extracting(Placement::getAdTypes)
                .containsExactly(Set.of(5061));
    }

    @Test
    public void makeHttpRequestsShouldPopulatePlacementSiteIdFromExtImpSiteId() {
        // given
        final Integer siteId = 123;
        final BidRequest bidRequest = givenBidRequest(impBuilder -> impBuilder.ext(
                mapper.valueToTree(ExtPrebid.of(null, ExtImpFlipp.builder()
                        .publisherNameIdentifier("publisherName")
                        .siteId(siteId)
                        .zoneIds(List.of(12))
                        .build()))));

        // when
        final Result<List<HttpRequest<CampaignRequestBody>>> result = target.makeHttpRequests(bidRequest);

        // then
        assertThat(result.getErrors()).isEmpty();
        assertThat(result.getValue())
                .extracting(HttpRequest::getPayload)
                .flatExtracting(CampaignRequestBody::getPlacements)
                .extracting(Placement::getSiteId)
                .containsExactly(siteId);
    }

    @Test
    public void makeHttpRequestsShouldPopulatePlacementZoneIdsIdFromExtImpZoneIds() {
        // given
        final Integer siteId = 123;
        final BidRequest bidRequest = givenBidRequest(impBuilder -> impBuilder.ext(
                mapper.valueToTree(ExtPrebid.of(null, ExtImpFlipp.builder()
                        .publisherNameIdentifier("publisherName")
                        .zoneIds(List.of(siteId))
                        .build()))));

        // when
        final Result<List<HttpRequest<CampaignRequestBody>>> result = target.makeHttpRequests(bidRequest);

        // then
        assertThat(result.getErrors()).isEmpty();
        assertThat(result.getValue())
                .extracting(HttpRequest::getPayload)
                .flatExtracting(CampaignRequestBody::getPlacements)
                .flatExtracting(Placement::getZoneIds)
                .containsExactly(siteId);
    }

    @Test
    public void makeHttpRequestsShouldContainPlacementDivNameByDefault() {
        // given
        final BidRequest bidRequest = givenBidRequest(identity());

        // when
        final Result<List<HttpRequest<CampaignRequestBody>>> result = target.makeHttpRequests(bidRequest);

        // then
        assertThat(result.getErrors()).isEmpty();
        assertThat(result.getValue())
                .extracting(HttpRequest::getPayload)
                .flatExtracting(CampaignRequestBody::getPlacements)
                .extracting(Placement::getDivName)
                .containsExactly("inline");
    }

    @Test
    public void makeHttpRequestsShouldContainPlacementCountByDefault() {
        // given
        final BidRequest bidRequest = givenBidRequest(identity());

        // when
        final Result<List<HttpRequest<CampaignRequestBody>>> result = target.makeHttpRequests(bidRequest);

        // then
        assertThat(result.getErrors()).isEmpty();
        assertThat(result.getValue())
                .extracting(HttpRequest::getPayload)
                .flatExtracting(CampaignRequestBody::getPlacements)
                .extracting(Placement::getCount)
                .containsExactly(1);
    }

    @Test
    public void makeHttpRequestsShouldCorrectlyPopulateAdTypesWhenCreativeTypeIsAnyValue() {
        // given
        final BidRequest bidRequest = givenBidRequest(impBuilder -> impBuilder.ext(
                mapper.valueToTree(ExtPrebid.of(null, ExtImpFlipp.builder()
                        .publisherNameIdentifier("publisherName")
                        .creativeType("HereCanBeAnyValue")
                        .zoneIds(List.of(12))
                        .build()))));

        // when
        final Result<List<HttpRequest<CampaignRequestBody>>> result = target.makeHttpRequests(bidRequest);

        // then
        assertThat(result.getErrors()).isEmpty();
        assertThat(result.getValue())
                .extracting(HttpRequest::getPayload)
                .flatExtracting(CampaignRequestBody::getPlacements)
                .extracting(Placement::getAdTypes)
                .containsExactly(Set.of(4309, 641));
    }

    @Test
    public void makeHttpRequestsShouldCorrectlyPopulatePlacementPrebid() {
        // given
        final BidRequest bidRequest = givenBidRequest(
                impBuilder -> impBuilder
                        .banner(Banner.builder().format(List.of(Format.builder().w(10).h(10).build())).build())
                        .ext(mapper.valueToTree(ExtPrebid.of(null, ExtImpFlipp.builder()
                                .publisherNameIdentifier("publisherName")
                                .creativeType("HereCanBeAnyValue")
                                .zoneIds(List.of(12))
                                .build()))));

        // when
        final Result<List<HttpRequest<CampaignRequestBody>>> result = target.makeHttpRequests(bidRequest);

        // then
        assertThat(result.getErrors()).isEmpty();
        assertThat(result.getValue())
                .extracting(HttpRequest::getPayload)
                .flatExtracting(CampaignRequestBody::getPlacements)
                .extracting(Placement::getPrebid)
                .containsExactly(PrebidRequest.builder()
                        .publisherNameIdentifier("publisherName")
                        .creativeType("HereCanBeAnyValue")
                        .requestId("123")
                        .width(10)
                        .height(10)
                        .build());
    }

    @Test
    public void makeHttpRequestsShouldNotPopulatePlacementWidthAndHeightWhenBannerFormantEmpty() {
        // given
        final BidRequest bidRequest = givenBidRequest(
                impBuilder -> impBuilder
                        .banner(Banner.builder().format(null).build())
                        .ext(mapper.valueToTree(ExtPrebid.of(null, ExtImpFlipp.builder()
                                .publisherNameIdentifier("publisherName")
                                .creativeType("HereCanBeAnyValue")
                                .zoneIds(List.of(12))
                                .build()))));

        // when
        final Result<List<HttpRequest<CampaignRequestBody>>> result = target.makeHttpRequests(bidRequest);

        // then
        assertThat(result.getErrors()).isEmpty();
        assertThat(result.getValue())
                .extracting(HttpRequest::getPayload)
                .flatExtracting(CampaignRequestBody::getPlacements)
                .extracting(Placement::getPrebid)
                .containsExactly(PrebidRequest.builder()
                        .publisherNameIdentifier("publisherName")
                        .creativeType("HereCanBeAnyValue")
                        .requestId("123")
                        .width(null)
                        .height(null)
                        .build());
    }

    @Test
    public void makeHttpRequestsShouldSetNullContentCodeWhenSitePageUnknownUrlAndOptionsContentCodeNull() {
        // given
        final BidRequest bidRequest = givenBidRequest(
                bidRequestBuilder -> bidRequestBuilder
                        .site(Site.builder().page("UnknownUrlLine").build()),
                impBuilder -> impBuilder
                        .banner(Banner.builder().format(null).build())
                        .ext(mapper.valueToTree(ExtPrebid.of(null, ExtImpFlipp.builder()
                                .publisherNameIdentifier("publisherName")
                                .options(ExtImpFlippOptions.of(false, false, null))
                                .zoneIds(List.of(12))
                                .build()))));

        // when
        final Result<List<HttpRequest<CampaignRequestBody>>> result = target.makeHttpRequests(bidRequest);

        // then
        assertThat(result.getErrors()).isEmpty();
        assertThat(result.getErrors()).isEmpty();
        assertThat(result.getValue())
                .extracting(HttpRequest::getPayload)
                .flatExtracting(CampaignRequestBody::getPlacements)
                .extracting(Placement::getProperties)
                .extracting(Properties::getContentCode)
                .containsNull();
    }

    @Test
    public void makeHttpRequestsShouldPopulateProperContentCodeWhenContentCodePresentInExtImpFlippOptions() {
        // given
        final BidRequest bidRequest = givenBidRequest(
                impBuilder -> impBuilder
                        .banner(Banner.builder().format(null).build())
                        .ext(mapper.valueToTree(ExtPrebid.of(null, ExtImpFlipp.builder()
                                .publisherNameIdentifier("publisherName")
                                .options(ExtImpFlippOptions.of(false, false, "AnyCode"))
                                .zoneIds(List.of(12))
                                .build()))));

        // when
        final Result<List<HttpRequest<CampaignRequestBody>>> result = target.makeHttpRequests(bidRequest);

        // then
        assertThat(result.getErrors()).isEmpty();
        assertThat(result.getErrors()).isEmpty();
        assertThat(result.getValue())
                .extracting(HttpRequest::getPayload)
                .flatExtracting(CampaignRequestBody::getPlacements)
                .extracting(Placement::getProperties)
                .extracting(Properties::getContentCode)
                .containsExactly("AnyCode");
    }

    @Test
    public void makeHttpRequestsShouldSetContentCodeFromSitePageWhenValidUrlContainFlippContentCodeWithValue() {
        // given
        final BidRequest bidRequest = givenBidRequest(
                bidRequestBuilder -> bidRequestBuilder
                        .site(Site.builder()
                                .page("http://www.example.com/test?flipp-content-code=value-test&any=any-value")
                                .build()),
                impBuilder -> impBuilder
                        .banner(Banner.builder().format(null).build())
                        .ext(mapper.valueToTree(ExtPrebid.of(null, ExtImpFlipp.builder()
                                .publisherNameIdentifier("publisherName")
                                .options(ExtImpFlippOptions.of(false, false, null))
                                .zoneIds(List.of(12))
                                .build()))));

        // when
        final Result<List<HttpRequest<CampaignRequestBody>>> result = target.makeHttpRequests(bidRequest);

        // then
        assertThat(result.getErrors()).isEmpty();
        assertThat(result.getValue())
                .extracting(HttpRequest::getPayload)
                .flatExtracting(CampaignRequestBody::getPlacements)
                .extracting(Placement::getProperties)
                .extracting(Properties::getContentCode)
                .containsExactly("value-test");
    }

    @Test
    public void makeHttpRequestsShouldSetUrlToRequestBodyWhenSitePagePresent() {
        // given
        final BidRequest bidRequest = givenBidRequest(
                bidRequestBuilder -> bidRequestBuilder
                        .site(Site.builder()
                                .page("http://www.example.com/test?flipp-content-code=value-test&any=any-value")
                                .build()),
                identity());

        // when
        final Result<List<HttpRequest<CampaignRequestBody>>> result = target.makeHttpRequests(bidRequest);

        // then
        assertThat(result.getErrors()).isEmpty();
        assertThat(result.getValue())
                .extracting(HttpRequest::getPayload)
                .flatExtracting(CampaignRequestBody::getUrl)
                .containsExactly("http://www.example.com/test?flipp-content-code=value-test&any=any-value");
    }

    @Test
    public void makeHttpRequestsShouldSetKeywordToRequestBodyWhenUserKeywords() {
        // given
        final BidRequest bidRequest = givenBidRequest(
                bidRequestBuilder -> bidRequestBuilder
                        .user(User.builder().keywords("Hi,bye").build()),
                identity());

        // when
        final Result<List<HttpRequest<CampaignRequestBody>>> result = target.makeHttpRequests(bidRequest);

        // then
        assertThat(result.getErrors()).isEmpty();
        assertThat(result.getValue())
                .extracting(HttpRequest::getPayload)
                .flatExtracting(CampaignRequestBody::getKeywords)
                .containsExactly("Hi", "bye");
    }

    @Test
    public void makeHttpRequestsShouldSetIpToRequestBodyWhenIpPresentInImpExtFlipp() {
        // given
        final BidRequest bidRequest = givenBidRequest(identity(),
                impBuilder -> impBuilder
                        .banner(Banner.builder().format(null).build())
                        .ext(mapper.valueToTree(ExtPrebid.of(null, ExtImpFlipp.builder()
                                .publisherNameIdentifier("publisherName")
                                .ip("Any-ip-address")
                                .options(ExtImpFlippOptions.of(false, false, null))
                                .zoneIds(List.of(12))
                                .build()))));

        // when
        final Result<List<HttpRequest<CampaignRequestBody>>> result = target.makeHttpRequests(bidRequest);

        // then
        assertThat(result.getErrors()).isEmpty();
        assertThat(result.getValue())
                .extracting(HttpRequest::getPayload)
                .flatExtracting(CampaignRequestBody::getIp)
                .containsExactly("Any-ip-address");
    }

    @Test
    public void makeHttpRequestsShouldSetIpToRequestBodyWhenPresentInDeviceIp() {
        // given
        final BidRequest bidRequest = givenBidRequest(
                bidRequestBuilder -> bidRequestBuilder.device(Device.builder().ip("any-iP").build()),
                impBuilder -> impBuilder
                        .ext(mapper.valueToTree(ExtPrebid.of(null, ExtImpFlipp.builder()
                                .publisherNameIdentifier("publisherName")
                                .ip(null)
                                .options(ExtImpFlippOptions.of(false, false, null))
                                .zoneIds(List.of(12))
                                .build()))));

        // when
        final Result<List<HttpRequest<CampaignRequestBody>>> result = target.makeHttpRequests(bidRequest);

        // then
        assertThat(result.getErrors()).isEmpty();
        assertThat(result.getValue())
                .extracting(HttpRequest::getPayload)
                .flatExtracting(CampaignRequestBody::getIp)
                .containsExactly("any-iP");
    }

    @Test
    public void makeHttpRequestsShouldAddErrorWhenExtImpAndDeviceIpAbsent() {
        // given
        final BidRequest bidRequest = givenBidRequest(
                bidRequestBuilder -> bidRequestBuilder.device(Device.builder().ip(null).build()),
                impBuilder -> impBuilder
                        .ext(mapper.valueToTree(ExtPrebid.of(null, ExtImpFlipp.builder()
                                .publisherNameIdentifier("publisherName")
                                .ip(null)
                                .options(ExtImpFlippOptions.of(false, false, null))
                                .zoneIds(List.of(12))
                                .build()))));

        // when
        final Result<List<HttpRequest<CampaignRequestBody>>> result = target.makeHttpRequests(bidRequest);

        // then
        assertThat(result.getValue()).isEmpty();
        assertThat(result.getErrors()).hasSize(2)
                .anySatisfy(error -> {
                    assertThat(error.getType()).isEqualTo(BidderError.Type.bad_input);
                    assertThat(error.getMessage()).startsWith("No IP set in Flipp bidder params or request device");
                });
    }

    @Test
    public void makeHttpRequestsShouldSetKeyToUserRequestBodyWhenUserIdPresent() {
        // given
        final BidRequest bidRequest = givenBidRequest(
                bidRequestBuilder -> bidRequestBuilder.user(User.builder().id("any-user-id").build()),
                identity());

        // when
        final Result<List<HttpRequest<CampaignRequestBody>>> result = target.makeHttpRequests(bidRequest);

        // then
        assertThat(result.getErrors()).isEmpty();
        assertThat(result.getValue())
                .extracting(HttpRequest::getPayload)
                .extracting(CampaignRequestBody::getUser)
                .extracting(CampaignRequestBodyUser::getKey)
                .containsExactly("any-user-id");
    }

    @Test
    public void makeHttpRequestsShouldSetKeyToUserRequestBodyWhenExtImpUserKeyPresent() {
        // given
        final BidRequest bidRequest = givenBidRequest(
                impBuilder -> impBuilder
                        .ext(mapper.valueToTree(ExtPrebid.of(null, ExtImpFlipp.builder()
                                .publisherNameIdentifier("publisherName")
                                .userKey("any-user-key")
                                .options(ExtImpFlippOptions.of(false, false, null))
                                .zoneIds(List.of(12))
                                .build()))));

        // when
        final Result<List<HttpRequest<CampaignRequestBody>>> result = target.makeHttpRequests(bidRequest);

        // then
        assertThat(result.getErrors()).isEmpty();
        assertThat(result.getValue())
                .extracting(HttpRequest::getPayload)
                .extracting(CampaignRequestBody::getUser)
                .extracting(CampaignRequestBodyUser::getKey)
                .containsExactly("any-user-key");
    }

    @Test
    public void makeHttpRequestsShouldGenerateKeyForUserRequestBodyWhenKeyAbsentInRequest() {
        // given
        final BidRequest bidRequest = givenBidRequest(
                bidRequestBuilder -> bidRequestBuilder.user(User.builder().id(null).build()),
                impBuilder -> impBuilder
                        .ext(mapper.valueToTree(ExtPrebid.of(null, ExtImpFlipp.builder()
                                .publisherNameIdentifier("publisherName")
                                .userKey(null)
                                .options(ExtImpFlippOptions.of(false, false, null))
                                .zoneIds(List.of(12))
                                .build()))));

        // when
        final Result<List<HttpRequest<CampaignRequestBody>>> result = target.makeHttpRequests(bidRequest);

        // then
        assertThat(result.getErrors()).isEmpty();
        assertThat(result.getValue())
                .extracting(HttpRequest::getPayload)
                .extracting(CampaignRequestBody::getUser)
                .extracting(CampaignRequestBodyUser::getKey)
                .isNotEmpty();
    }

    @Test
    public void makeHttpRequestsShouldTakePrecedenceKeyFromUserRequestWhenPresentKeyInBothPlaces() {
        // given
        final BidRequest bidRequest = givenBidRequest(
                bidRequestBuilder -> bidRequestBuilder.user(User.builder().id("request-level").build()),
                impBuilder -> impBuilder
                        .ext(mapper.valueToTree(ExtPrebid.of(null, ExtImpFlipp.builder()
                                .publisherNameIdentifier("publisherName")
                                .userKey("ext-imp-level")
                                .options(ExtImpFlippOptions.of(false, false, null))
                                .zoneIds(List.of(12))
                                .build()))));

        // when
        final Result<List<HttpRequest<CampaignRequestBody>>> result = target.makeHttpRequests(bidRequest);

        // then
        assertThat(result.getErrors()).isEmpty();
        assertThat(result.getValue())
                .extracting(HttpRequest::getPayload)
                .extracting(CampaignRequestBody::getUser)
                .extracting(CampaignRequestBodyUser::getKey)
                .containsExactly("request-level");
    }

    @Test
    public void makeHttpRequestsShouldAddHeaderWhenDeviceUaPresent() {
        // given
        final String ua = "Any-ua-str";
        final BidRequest bidRequest = givenBidRequest(
                bidRequestBuilder -> bidRequestBuilder.device(Device.builder()
                        .ip("any-id")
                        .ua(ua)
                        .build()),
                identity());

        // when
        final Result<List<HttpRequest<CampaignRequestBody>>> result = target.makeHttpRequests(bidRequest);

        // then
        assertThat(result.getErrors()).isEmpty();
        assertThat(result.getValue())
                .extracting(HttpRequest::getHeaders)
                .flatExtracting(MultiMap::entries)
                .extracting(Map.Entry::getKey, Map.Entry::getValue)
                .containsExactlyInAnyOrder(
                        tuple(HttpUtil.CONTENT_TYPE_HEADER.toString(), HttpUtil.APPLICATION_JSON_CONTENT_TYPE),
                        tuple(HttpUtil.ACCEPT_HEADER.toString(), HttpHeaderValues.APPLICATION_JSON.toString()),
                        tuple(HttpUtil.USER_AGENT_HEADER.toString(), ua)
                );
    }

    @Test
    public void makeBidsShouldReturnErrorWhenCampaignResponseBodyIsInvalid() throws JsonProcessingException {
        // given
        final BidderCall<CampaignRequestBody> httpCall = givenHttpCall(CampaignRequestBody.builder().build(),
                mapper.writeValueAsString("InvalidValue"));

        // and
        final BidRequest bidRequest = givenBidRequest(identity());

        // when
        final Result<List<BidderBid>> result = target.makeBids(httpCall, bidRequest);

        // then
        assertThat(result.getValue()).isEmpty();
        assertThat(result.getErrors()).hasSize(1)
                .anySatisfy(error -> {
                    assertThat(error.getType()).isEqualTo(BidderError.Type.bad_server_response);
                    assertThat(error.getMessage()).startsWith("Failed to decode: Cannot construct instance of "
                            + "`org.prebid.server.bidder.flipp.model.response.CampaignResponseBody`");
                });
    }

    @Test
    public void makeBidsShouldReturnEmptyBidderBidWhenCampaignResponseBodyNull() throws JsonProcessingException {
        // given
        final BidderCall<CampaignRequestBody> httpCall = givenHttpCall(CampaignRequestBody.builder().build(),
                mapper.writeValueAsString(CampaignResponseBody.of(null, null)));

        // and
        final BidRequest bidRequest = givenBidRequest(identity());

        // when
        final Result<List<BidderBid>> result = target.makeBids(httpCall, bidRequest);

        // then
        assertThat(result.getErrors()).isEmpty();
        assertThat(result.getValue()).isEmpty();
    }

    @Test
    public void makeBidsShouldReturnEmptyBidderBidWhenInlineEmpty() throws JsonProcessingException {
        // given
        final BidderCall<CampaignRequestBody> httpCall = givenHttpCall(CampaignRequestBody.builder().build(),
                mapper.writeValueAsString(CampaignResponseBody.of(null, null)));

        // and
        final BidRequest bidRequest = givenBidRequest(identity());

        // when
        final Result<List<BidderBid>> result = target.makeBids(httpCall, bidRequest);

        // then
        assertThat(result.getErrors()).isEmpty();
        assertThat(result.getValue()).isEmpty();
    }

    @Test
    public void makeBidsShouldPopulateBidCridFromInlineCreativeId() throws JsonProcessingException {
        // given
        final BidRequest bidRequest = givenBidRequest(identity());

        // and
        final Integer creativeId = 1;
        final BidderCall<CampaignRequestBody> httpCall = givenHttpCall(CampaignRequestBody.builder().build(),
                mapper.writeValueAsString(givenCampaignResponseBody(inlineBuilder ->
                        inlineBuilder.creativeId(creativeId))));

        // when
        final Result<List<BidderBid>> result = target.makeBids(httpCall, bidRequest);

        // then
        assertThat(result.getErrors()).isEmpty();
        assertThat(result.getValue()).hasSize(1)
                .extracting(BidderBid::getBid)
                .extracting(Bid::getCrid)
                .containsExactly(creativeId.toString());
    }

    @Test
    public void makeBidsShouldPopulateBidPriceFromInlinePrebidCpm() throws JsonProcessingException {
        // given
        final BidRequest bidRequest = givenBidRequest(identity());

        // and
        final BigDecimal price = BigDecimal.ONE;
        final BidderCall<CampaignRequestBody> httpCall = givenHttpCall(CampaignRequestBody.builder().build(),
                mapper.writeValueAsString(givenCampaignResponseBody(inlineBuilder ->
                        inlineBuilder.prebid(Prebid.of(price, "any", "any", "123")))));

        // when
        final Result<List<BidderBid>> result = target.makeBids(httpCall, bidRequest);

        // then
        assertThat(result.getErrors()).isEmpty();
        assertThat(result.getValue()).hasSize(1)
                .extracting(BidderBid::getBid)
                .extracting(Bid::getPrice)
                .containsExactly(price);
    }

    @Test
    public void makeBidsShouldPopulateBidAdmFromInlineCreative() throws JsonProcessingException {
        // given
        final BidRequest bidRequest = givenBidRequest(identity());

        // and
        final String creative = "creative";
        final BidderCall<CampaignRequestBody> httpCall = givenHttpCall(CampaignRequestBody.builder().build(),
                mapper.writeValueAsString(givenCampaignResponseBody(
                        inlineBuilder -> inlineBuilder
                                .prebid(Prebid.of(BigDecimal.ONE, creative, "crType", "123")))));

        // when
        final Result<List<BidderBid>> result = target.makeBids(httpCall, bidRequest);

        // then
        assertThat(result.getErrors()).isEmpty();
        assertThat(result.getValue()).hasSize(1)
                .extracting(BidderBid::getBid)
                .extracting(Bid::getAdm)
                .containsExactly(creative);
    }

    @Test
    public void makeBidsShouldPopulateBidIdFromInlineAdId() throws JsonProcessingException {
        // given
        final BidRequest bidRequest = givenBidRequest(identity());

        // and
        final Integer adId = 12;
        final BidderCall<CampaignRequestBody> httpCall = givenHttpCall(CampaignRequestBody.builder().build(),
                mapper.writeValueAsString(givenCampaignResponseBody(inlineBuilder -> inlineBuilder.adId(adId))));

        // when
        final Result<List<BidderBid>> result = target.makeBids(httpCall, bidRequest);

        // then
        assertThat(result.getErrors()).isEmpty();
        assertThat(result.getValue()).hasSize(1)
                .extracting(BidderBid::getBid)
                .extracting(Bid::getId)
                .containsExactly(String.valueOf(adId));
    }

    @Test
    public void makeBidsShouldPopulateBidImpIdFromBidRequestImpId() throws JsonProcessingException {
        // given
        final BidRequest bidRequest = givenBidRequest(identity());

        // and
        final BidderCall<CampaignRequestBody> httpCall = givenHttpCall(CampaignRequestBody.builder().build(),
                mapper.writeValueAsString(givenCampaignResponseBody(identity())));

        // when
        final Result<List<BidderBid>> result = target.makeBids(httpCall, bidRequest);

        // then
        assertThat(result.getErrors()).isEmpty();
        assertThat(result.getValue()).hasSize(1)
                .extracting(BidderBid::getBid)
                .extracting(Bid::getImpid)
                .containsExactly("123");
    }

    @Test
    public void makeBidsShouldPopulateBidWidthFromInlineContentsDataWidth() throws JsonProcessingException {
        // given
        final BidRequest bidRequest = givenBidRequest(identity());

        // and
        final Integer width = 22;
        final BidderCall<CampaignRequestBody> httpCall = givenHttpCall(CampaignRequestBody.builder().build(),
                mapper.writeValueAsString(givenCampaignResponseBody(inlineBuilder -> inlineBuilder
                        .contents(singletonList(Content.of("any", "custom",
                                Data.of(null, 0, width), "type"))))));

        // when
        final Result<List<BidderBid>> result = target.makeBids(httpCall, bidRequest);

        // then
        assertThat(result.getErrors()).isEmpty();
        assertThat(result.getValue()).hasSize(1)
                .extracting(BidderBid::getBid)
                .extracting(Bid::getW)
                .containsExactly(width);
    }

    @Test
    public void makeBidsShouldPopulateBidWidthWithNullWhenInlineContentsDataWidthEmpty()
            throws JsonProcessingException {
        // given
        final BidRequest bidRequest = givenBidRequest(identity());

        // and
        final BidderCall<CampaignRequestBody> httpCall = givenHttpCall(CampaignRequestBody.builder().build(),
                mapper.writeValueAsString(givenCampaignResponseBody(inlineBuilder -> inlineBuilder
                        .contents(singletonList(
                                Content.of("any", "custom", null, "type"))))));

        // when
        final Result<List<BidderBid>> result = target.makeBids(httpCall, bidRequest);

        // then
        assertThat(result.getErrors()).isEmpty();
        assertThat(result.getValue()).hasSize(1)
                .extracting(BidderBid::getBid)
                .extracting(Bid::getW)
                .containsNull();
    }

    @Test
    public void makeBidsShouldPopulateBidHeightWithZeroWhenInlineContentsIsPresent() throws JsonProcessingException {
        // given
        final BidRequest bidRequest = givenBidRequest(identity());

        // and
        final BidderCall<CampaignRequestBody> httpCall = givenHttpCall(CampaignRequestBody.builder().build(),
                mapper.writeValueAsString(givenCampaignResponseBody(inlineBuilder ->
                        inlineBuilder.contents(singletonList(
                                Content.of("any", "custom", null, "type"))))));

        // when
        final Result<List<BidderBid>> result = target.makeBids(httpCall, bidRequest);

        // then
        assertThat(result.getErrors()).isEmpty();
        assertThat(result.getValue()).hasSize(1)
                .extracting(BidderBid::getBid)
                .extracting(Bid::getH)
                .containsExactly(0);
    }

    @Test
    public void makeBidsShouldPopulateBidHeightWithNullWhenInlineContentsIsAbsent() throws JsonProcessingException {
        // given
        final BidRequest bidRequest = givenBidRequest(identity());

        // and
        final BidderCall<CampaignRequestBody> httpCall = givenHttpCall(CampaignRequestBody.builder().build(),
                mapper.writeValueAsString(givenCampaignResponseBody(inlineBuilder ->
                        inlineBuilder.contents(null))));

        // when
        final Result<List<BidderBid>> result = target.makeBids(httpCall, bidRequest);

        // then
        assertThat(result.getErrors()).isEmpty();
        assertThat(result.getValue()).hasSize(1)
                .extracting(BidderBid::getBid)
                .extracting(Bid::getW)
                .containsNull();
    }

    @Test
    public void makeBidsShouldPopulateBidCurrencyAsUsdByDefault() throws JsonProcessingException {
        // given
        final BidRequest bidRequest = givenBidRequest(identity());

        // and
        final BidderCall<CampaignRequestBody> httpCall = givenHttpCall(CampaignRequestBody.builder().build(),
                mapper.writeValueAsString(givenCampaignResponseBody(identity())));

        // when
        final Result<List<BidderBid>> result = target.makeBids(httpCall, bidRequest);

        // then
        assertThat(result.getErrors()).isEmpty();
        assertThat(result.getValue()).hasSize(1)
                .extracting(BidderBid::getBidCurrency)
                .containsExactly("USD");
    }

    @Test
    public void makeBidsShouldPopulateBidTypeAsBannerByDefault() throws JsonProcessingException {
        // given
        final BidRequest bidRequest = givenBidRequest(identity());

        // and
        final BidderCall<CampaignRequestBody> httpCall = givenHttpCall(CampaignRequestBody.builder().build(),
                mapper.writeValueAsString(givenCampaignResponseBody(identity())));

        // when
        final Result<List<BidderBid>> result = target.makeBids(httpCall, bidRequest);

        // then
        assertThat(result.getErrors()).isEmpty();
        assertThat(result.getValue()).hasSize(1)
                .extracting(BidderBid::getType)
                .containsExactly(banner);
    }

    private static BidRequest givenBidRequest(
            UnaryOperator<BidRequest.BidRequestBuilder> bidRequestCustomizer,
            UnaryOperator<Imp.ImpBuilder> impCustomizer) {

        return bidRequestCustomizer.apply(BidRequest.builder()
                        .device(Device.builder().ip("anyId").build())
                        .imp(singletonList(givenImp(impCustomizer))))
                .build();
    }

    private static BidRequest givenBidRequest(UnaryOperator<Imp.ImpBuilder> impCustomizer) {
        return givenBidRequest(identity(), impCustomizer);
    }

    private static Imp givenImp(UnaryOperator<Imp.ImpBuilder> impCustomizer) {
        return impCustomizer.apply(Imp.builder()
                        .id("123")
                        .banner(Banner.builder().w(23).h(25).build())
                        .ext(mapper.valueToTree(ExtPrebid.of(null, ExtImpFlipp.builder()
                                .publisherNameIdentifier("publisherName")
                                .creativeType("Any")
                                .zoneIds(List.of(12))
                                .build()))))
                .build();
    }

    private static CampaignResponseBody givenCampaignResponseBody(UnaryOperator<Inline.InlineBuilder> inlineCustomize) {
        return CampaignResponseBody.of(null,
                Decisions.of(singletonList(inlineCustomize.apply(Inline.builder()
                        .creativeId(1)
                        .prebid(Prebid.of(BigDecimal.ONE, "creative", "creativeType", "123"))
                        .adId(1)).build())));
    }

    private static BidderCall<CampaignRequestBody> givenHttpCall(CampaignRequestBody campaignRequestBody, String body) {
        return BidderCall.succeededHttp(
                HttpRequest.<CampaignRequestBody>builder().payload(campaignRequestBody).build(),
                HttpResponse.of(200, null, body),
                null);
    }
}
