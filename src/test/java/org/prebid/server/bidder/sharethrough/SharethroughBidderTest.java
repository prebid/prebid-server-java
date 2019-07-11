package org.prebid.server.bidder.sharethrough;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.iab.openrtb.request.App;
import com.iab.openrtb.request.Banner;
import com.iab.openrtb.request.BidRequest;
import com.iab.openrtb.request.Device;
import com.iab.openrtb.request.Imp;
import com.iab.openrtb.request.Site;
import com.iab.openrtb.response.Bid;
import io.vertx.core.http.HttpMethod;
import io.vertx.core.json.Json;
import org.junit.Before;
import org.junit.Test;
import org.prebid.server.VertxTest;
import org.prebid.server.bidder.model.BidderBid;
import org.prebid.server.bidder.model.BidderError;
import org.prebid.server.bidder.model.HttpCall;
import org.prebid.server.bidder.model.HttpRequest;
import org.prebid.server.bidder.model.HttpResponse;
import org.prebid.server.bidder.model.Result;
import org.prebid.server.bidder.sharethrough.model.bidResponse.ExtImpSharethroughCreative;
import org.prebid.server.bidder.sharethrough.model.bidResponse.ExtImpSharethroughCreativeMetadata;
import org.prebid.server.bidder.sharethrough.model.bidResponse.ExtImpSharethroughResponse;
import org.prebid.server.proto.openrtb.ext.ExtPrebid;
import org.prebid.server.proto.openrtb.ext.request.sharethrough.ExtImpSharethrough;
import org.prebid.server.proto.openrtb.ext.response.BidType;
import org.prebid.server.util.HttpUtil;

import java.math.BigDecimal;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

import static java.util.Collections.emptyMap;
import static java.util.Collections.singletonList;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;
import static org.assertj.core.api.Assertions.tuple;

public class SharethroughBidderTest extends VertxTest {

    private static final String ENDPOINT_URL = "https://test.endpoint.com";

    private SharethroughBidder sharethroughBidder;

    @Before
    public void setUp() {
        sharethroughBidder = new SharethroughBidder(ENDPOINT_URL);
    }

    @Test
    public void creationShouldFailOnInvalidEndpointUrl() {
        assertThatIllegalArgumentException().isThrownBy(() -> new SharethroughBidder("invalid_url"));
    }

    @Test
    public void makeHttpRequestsShouldReturnErrorWhenSiteIsNotPresent() {
        // given
        final BidRequest bidRequest = BidRequest.builder()
                .imp(singletonList(Imp.builder()
                        .ext(mapper.valueToTree(ExtPrebid.of(null, mapper.createArrayNode())))
                        .build()))
                .id("request_id")
                .build();

        // when
        final Result<List<HttpRequest<Void>>> result = sharethroughBidder.makeHttpRequests(bidRequest);

        // then
        assertThat(result.getErrors()).hasSize(1);
        assertThat(result.getErrors().get(0).getMessage()).startsWith("site.page is required");
        assertThat(result.getValue()).isEmpty();
    }

    @Test
    public void makeHttpRequestsShouldReturnErrorWhenImpExtCouldNotBeParsed() {
        // given
        final BidRequest bidRequest = BidRequest.builder()
                .imp(singletonList(Imp.builder()
                        .ext(mapper.valueToTree(ExtPrebid.of(null, mapper.createArrayNode())))
                        .build()))
                .id("request_id")
                .site(Site.builder().page("http://page.com").build())
                .build();

        // when
        final Result<List<HttpRequest<Void>>> result = sharethroughBidder.makeHttpRequests(bidRequest);

        // then
        assertThat(result.getErrors()).hasSize(1);
        assertThat(result.getErrors().get(0).getMessage()).startsWith("Error occurred parsing sharethrough parameters");
        assertThat(result.getValue()).isEmpty();
    }

    @Test
    public void makeHttpRequestsShouldReturnRequestWithHeaderOriginDomainAndBodyNull() {
        // given
        final BidRequest bidRequest = BidRequest.builder()
                .imp(singletonList(Imp.builder()
                        .ext(mapper.valueToTree(ExtPrebid.of(null,
                                ExtImpSharethrough.of("pkey", false, Arrays.asList(100, 200)))))
                        .build()))
                .id("request_id")
                .site(Site.builder().page("http://page.com").build())
                .build();

        // when
        final Result<List<HttpRequest<Void>>> result = sharethroughBidder.makeHttpRequests(bidRequest);

        // then
        assertThat(result.getErrors()).isEmpty();
        assertThat(result.getValue()).hasSize(1).element(0)
                .returns(null, HttpRequest::getBody);
        assertThat(result.getValue().get(0).getHeaders()).isNotNull()
                .extracting(Map.Entry::getKey, Map.Entry::getValue)
                .containsOnly(
                        tuple(HttpUtil.CONTENT_TYPE_HEADER.toString(), "application/json;charset=utf-8"),
                        tuple(HttpUtil.ORIGIN_HEADER.toString(), "page.com"),
                        tuple(HttpUtil.ACCEPT_HEADER.toString(), "application/json"));
    }

    @Test
    public void makeHttpRequestsShouldReturnRequestWithCorrectUriAndHeaders() {
        // given
        final BidRequest bidRequest = BidRequest.builder()
                .imp(singletonList(Imp.builder()
                        .id("abc")
                        .ext(mapper.valueToTree(ExtPrebid.of(null,
                                ExtImpSharethrough.of("pkey", false, Arrays.asList(10, 20)))))
                        .banner(Banner.builder().w(40).h(30).build())
                        .build()))
                .app(App.builder().ext(Json.mapper.createObjectNode()).build())
                .site(Site.builder().page("http://page.com").build())
                .device(Device.builder().ua("Android Chrome/60.0.3112").build())
                .build();

        // when
        final Result<List<HttpRequest<Void>>> result = sharethroughBidder.makeHttpRequests(bidRequest);

        // then
        final String expectedParameters = "?placement_key=pkey&bidId=abc&consent_required=false&consent_string=&instant_play_capable=true&stayInIframe=false&height=10&width=20&supplyId=FGMrCMMc&strVersion=1.0.0";

        assertThat(result.getErrors()).isEmpty();
        assertThat(result.getValue()).doesNotContainNull()
                .hasSize(1).element(0)
                .returns(HttpMethod.POST, HttpRequest::getMethod)
                .returns(null, HttpRequest::getBody)
                .returns(null, HttpRequest::getPayload)
                .returns(ENDPOINT_URL + expectedParameters, HttpRequest::getUri);
    }

    @Test
    public void makeHttpRequestsShouldReturnRequestWithCorrectUriAndHeadersDefaultParameters() {
        // given
        final BidRequest bidRequest = BidRequest.builder()
                .imp(singletonList(Imp.builder()
                        .ext(mapper.valueToTree(ExtPrebid.of(null,
                                ExtImpSharethrough.of("pkey", false, null))))
                        .build()))
                .site(Site.builder().page("http://page.com").build())
                .build();

        // when
        final Result<List<HttpRequest<Void>>> result = sharethroughBidder.makeHttpRequests(bidRequest);

        // then
        final String expectedParameters = "?placement_key=pkey&bidId&consent_required=false&consent_string=&instant_play_capable=false&stayInIframe=false&height=1&width=1&supplyId=FGMrCMMc&strVersion=1.0.0";

        assertThat(result.getErrors()).isEmpty();
        assertThat(result.getValue()).doesNotContainNull()
                .hasSize(1).element(0)
                .returns(HttpMethod.POST, HttpRequest::getMethod)
                .returns(null, HttpRequest::getBody)
                .returns(null, HttpRequest::getPayload)
                .returns(ENDPOINT_URL + expectedParameters, HttpRequest::getUri);
    }

    @Test
    public void makeBidsShouldReturnCorrectBidderBid() throws JsonProcessingException {
        // given
        final ExtImpSharethroughCreativeMetadata creativeMetadata = ExtImpSharethroughCreativeMetadata.builder()
                .campaignKey("cmpKey")
                .creativeKey("creaKey")
                .dealId("dealId")
                .build();

        final ExtImpSharethroughCreative metadata = ExtImpSharethroughCreative.of(
                null, BigDecimal.valueOf(10), creativeMetadata, 0);

        final ExtImpSharethroughResponse response = ExtImpSharethroughResponse.builder()
                .adserverRequestId("arid")
                .bidId("bid")
                .creatives(singletonList(metadata))
                .build();

        final String uri = "http://uri.com?placement_key=pkey&bidId=bidid&height=20&width=30";

        final HttpCall<Void> httpCall = givenHttpCallWithUri(uri, Json.mapper.writeValueAsString(response));

        // when
        final Result<List<BidderBid>> result = sharethroughBidder.makeBids(httpCall, null);

        // then
        final String adm = "<img src=\"//b.sharethrough.com/butler?type=s2s-win&arid=arid\" />\n" +
                "\t\t<div data-str-native-key=\"pkey\" data-stx-response-name=\"str_response_bid\"></div>\n" +
                //Decoded: {"adserverRequestId":"arid","bidId":"bid","creatives":[{"cpm":10,"creative":{"campaign_key":"cmpKey","creative_key":"creaKey","deal_id":"dealId"},"version":0}]}
                "\t\t<script>var str_response_bid = \"eyJhZHNlcnZlclJlcXVlc3RJZCI6ImFyaWQiLCJiaWRJZCI6ImJpZCIsImNyZWF0aXZlcyI6W3siY3BtIjoxMCwiY3JlYXRpdmUiOnsiY2FtcGFpZ25fa2V5IjoiY21wS2V5IiwiY3JlYXRpdmVfa2V5IjoiY3JlYUtleSIsImRlYWxfaWQiOiJkZWFsSWQifSwidmVyc2lvbiI6MH1dfQ==\"</script>\n" +
                "\t\t\t<script src=\"//native.sharethrough.com/assets/sfp-set-targeting.js\"></script>\n" +
                "\t    \t<script>\n" +
                "\t     (function() {\n" +
                "\t     if (!(window.STR && window.STR.Tag) && !(window.top.STR && window.top.STR.Tag)){\n" +
                "\t         var sfp_js = document.createElement('script');\n" +
                "\t         sfp_js.src = \"//native.sharethrough.com/assets/sfp.js\";\n" +
                "\t         sfp_js.type = 'text/javascript';\n" +
                "\t         sfp_js.charset = 'utf-8';\n" +
                "\t         try {\n" +
                "\t             window.top.document.getElementsByTagName('body')[0].appendChild(sfp_js);\n" +
                "\t         } catch (e) {\n" +
                "\t           console.log(e);\n" +
                "\t         }\n" +
                "\t       }\n" +
                "\t     })()\n" +
                "\t\t   </script>\n";

        final BidderBid expected = BidderBid.of(
                Bid.builder()
                        .adid("arid")
                        .id("bid")
                        .impid("bidid")
                        .price(BigDecimal.valueOf(10))
                        .cid("cmpKey")
                        .crid("creaKey")
                        .dealid("dealId")
                        .w(30)
                        .h(20)
                        .adm(adm)
                        .build(),
                BidType.xNative, "USD");

        assertThat(result.getValue().get(0).getBid().getAdm()).isEqualTo(adm);
        assertThat(result.getErrors()).isEmpty();
        assertThat(result.getValue()).doesNotContainNull()
                .hasSize(1).element(0).isEqualTo(expected);
    }

    @Test
    public void makeBidsShouldReturnErrorIfResponseBodyCouldNotBeParsed() {
        // given
        final HttpCall<Void> httpCall = givenHttpCall(null, "invalid");

        // when
        final Result<List<BidderBid>> result = sharethroughBidder.makeBids(httpCall, null);

        // then
        assertThat(result.getErrors()).hasSize(1);
        assertThat(result.getErrors().get(0).getType()).isEqualTo(BidderError.Type.bad_server_response);
        assertThat(result.getValue()).isEmpty();
    }

    @Test
    public void extractTargetingShouldReturnEmptyMap() {
        assertThat(sharethroughBidder.extractTargeting(mapper.createObjectNode())).isEqualTo(emptyMap());
    }

    private static HttpCall<Void> givenHttpCall(Void bidRequest, String body) {
        return HttpCall.success(
                HttpRequest.<Void>builder().payload(bidRequest).build(),
                HttpResponse.of(200, null, body),
                null);
    }

    private static HttpCall<Void> givenHttpCallWithUri(String uri, String body) {
        return HttpCall.success(
                HttpRequest.<Void>builder().uri(uri).build(),
                HttpResponse.of(200, null, body),
                null);
    }
}

