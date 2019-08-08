package org.prebid.server.bidder.emx_digital;

import static java.util.Collections.emptyMap;
import static java.util.Collections.singletonList;
import static java.util.function.Function.identity;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;
import static org.assertj.core.api.Assertions.tuple;
import static org.prebid.server.proto.openrtb.ext.response.BidType.banner;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.iab.openrtb.request.Banner;
import com.iab.openrtb.request.Banner.BannerBuilder;
import com.iab.openrtb.request.BidRequest;
import com.iab.openrtb.request.Device;
import com.iab.openrtb.request.Format;
import com.iab.openrtb.request.Imp;
import com.iab.openrtb.request.Site;
import com.iab.openrtb.response.Bid;
import com.iab.openrtb.response.BidResponse;
import com.iab.openrtb.response.SeatBid;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
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
import org.prebid.server.proto.openrtb.ext.request.adkerneladn.ExtImpAdkernelAdn;
import org.prebid.server.proto.openrtb.ext.request.emx_digital.ExtImpEmxDigital;

public class EmxDigitalBidderTest extends VertxTest {

	private static final String ENDPOINT_URL = "https://test.endpoint.com";

	private EmxDigitalBidder emxDigitalBidder;

	@Before
	public void setUp() {
		emxDigitalBidder = new EmxDigitalBidder(ENDPOINT_URL);
	}

	@Test
	public void creationShouldFailOnInvalidEndpointUrl() {
		assertThatIllegalArgumentException().isThrownBy(() -> new EmxDigitalBidder("invalid_url"));
	}

	@Test
	public void makeHttpRequestsShouldReturnErrorWhenImpExtCouldNotBeParsed() {
		// given
		final BidRequest bidRequest = BidRequest.builder()
			.imp(singletonList(Imp.builder()
				.ext(mapper.valueToTree(ExtPrebid.of(null, mapper.createArrayNode())))
				.build()))
			.id("request_id")
			.build();

		// when
		final Result<List<HttpRequest<BidRequest>>> result = emxDigitalBidder
			.makeHttpRequests(bidRequest);

		// then
		assertThat(result.getErrors()).hasSize(2);
		assertThat(result.getErrors().get(0).getMessage())
			.startsWith("Cannot deserialize instance");
		assertThat(result.getErrors().get(1).getMessage())
			.startsWith("Error in makeBidRequest of Imp");
		assertThat(result.getValue()).isEmpty();
	}

	@Test
	public void makeHttpRequestsShouldReturnErrorWhenImpExtNotContainsBanner() {
		// given
		final BidRequest bidRequest = BidRequest.builder()
			.imp(singletonList(Imp.builder()
				.ext(mapper.valueToTree(ExtPrebid.of(null, ExtImpEmxDigital.of("123", ""))))
				.build()))
			.id("request_id")
			.build();

		// when
		final Result<List<HttpRequest<BidRequest>>> result = emxDigitalBidder
			.makeHttpRequests(bidRequest);

		// then
		assertThat(result.getErrors()).hasSize(2);
		assertThat(result.getErrors().get(0).getMessage())
			.startsWith("Request needs to include a Banner object");
		assertThat(result.getErrors().get(1).getMessage())
			.startsWith("Error in makeBidRequest of Imp");
		assertThat(result.getValue()).isEmpty();
	}

	@Test
	public void makeHttpRequestsShouldReturnErrorWhenImpExtEmxDigitalTagidIsNull() {
		// given
		final BidRequest bidRequest = BidRequest.builder()
			.imp(singletonList(Imp.builder()
				.banner(Banner.builder().build())
				.ext(mapper.valueToTree(ExtPrebid.of(null, ExtImpEmxDigital.of(null, ""))))
				.build()))
			.id("request_id")
			.build();

		// when
		final Result<List<HttpRequest<BidRequest>>> result = emxDigitalBidder
			.makeHttpRequests(bidRequest);

		// then
		assertThat(result.getErrors()).hasSize(2);
		assertThat(result.getErrors().get(0).getMessage())
			.startsWith("tagid must be a String of numbers");
		assertThat(result.getErrors().get(1).getMessage())
			.startsWith("Error in makeBidRequest of Imp");
		assertThat(result.getValue()).isEmpty();
	}

	@Test
	public void makeHttpRequestsShouldReturnErrorWhenImpExtEmxDigitalTagidIsNotNumber() {
		// given
		final BidRequest bidRequest = BidRequest.builder()
			.imp(singletonList(Imp.builder()
				.banner(Banner.builder().build())
				.ext(mapper.valueToTree(ExtPrebid.of(null, ExtImpEmxDigital.of("not", ""))))
				.build()))
			.id("request_id")
			.build();

		// when
		final Result<List<HttpRequest<BidRequest>>> result = emxDigitalBidder
			.makeHttpRequests(bidRequest);

		// then
		assertThat(result.getErrors()).hasSize(2);
		assertThat(result.getErrors().get(0).getMessage())
			.startsWith("tagid must be a String of numbers");
		assertThat(result.getErrors().get(1).getMessage())
			.startsWith("Error in makeBidRequest of Imp");
		assertThat(result.getValue()).isEmpty();
	}

	@Test
	public void makeHttpRequestsShouldReturnErrorWhenImpExtEmxDigitalTagidIsZero() {
		// given
		final BidRequest bidRequest = BidRequest.builder()
			.imp(singletonList(Imp.builder()
				.banner(Banner.builder().build())
				.ext(mapper.valueToTree(ExtPrebid.of(null, ExtImpEmxDigital.of("0", ""))))
				.build()))
			.id("request_id")
			.build();

		// when
		final Result<List<HttpRequest<BidRequest>>> result = emxDigitalBidder
			.makeHttpRequests(bidRequest);

		// then
		assertThat(result.getErrors()).hasSize(2);
		assertThat(result.getErrors().get(0).getMessage())
			.startsWith("tagid cant be 0");
		assertThat(result.getErrors().get(1).getMessage())
			.startsWith("Error in makeBidRequest of Imp");
		assertThat(result.getValue()).isEmpty();
	}

	@Test
	public void makeHttpRequestsShouldReturnErrorWhenWAndHIsNullAndBannerFormatIsNull() {
		// given
		final BidRequest bidRequest = BidRequest.builder()
			.imp(singletonList(Imp.builder()
				.banner(Banner.builder().build())
				.ext(mapper.valueToTree(ExtPrebid.of(null, ExtImpEmxDigital.of("1", "1"))))
				.build()))
			.id("request_id")
			.build();

		// when
		final Result<List<HttpRequest<BidRequest>>> result = emxDigitalBidder
			.makeHttpRequests(bidRequest);

		// then
		assertThat(result.getErrors()).hasSize(2);
		assertThat(result.getErrors().get(0).getMessage())
			.startsWith("Need at least one size to build request");
		assertThat(result.getErrors().get(1).getMessage())
			.startsWith("Error in makeBidRequest of Imp");
		assertThat(result.getValue()).isEmpty();
	}

	@Test
	public void makeHttpRequestsShouldReturnErrorWhenWAndHIsNullAndBannerFormatIsEmpty() {
		// given
		final BidRequest bidRequest = BidRequest.builder()
			.imp(singletonList(Imp.builder()
				.banner(Banner.builder().format(Collections.emptyList()).build())
				.ext(mapper.valueToTree(ExtPrebid.of(null, ExtImpEmxDigital.of("1", "1"))))
				.build()))
			.id("request_id")
			.build();

		// when
		final Result<List<HttpRequest<BidRequest>>> result = emxDigitalBidder
			.makeHttpRequests(bidRequest);

		// then
		assertThat(result.getErrors()).hasSize(2);
		assertThat(result.getErrors().get(0).getMessage())
			.startsWith("Need at least one size to build request");
		assertThat(result.getErrors().get(1).getMessage())
			.startsWith("Error in makeBidRequest of Imp");
		assertThat(result.getValue()).isEmpty();
	}

	@Test
	public void makeHttpRequestsShouldModifyImpWhenExtImpEmxDigitalContainsRequredValues() {
		// given
		final BidRequest bidRequest = BidRequest.builder()
			.imp(singletonList(Imp.builder()
				.banner(Banner.builder().w(100).h(100).build())
				.ext(mapper.valueToTree(ExtPrebid.of(null, ExtImpEmxDigital.of("123", "2"))))
				.build()))
			.site(Site.builder().page("https://exmaple/").build())
			.id("request_id")
			.build();

		// when
		final Result<List<HttpRequest<BidRequest>>> result = emxDigitalBidder
			.makeHttpRequests(bidRequest);

		// then
		assertThat(result.getErrors()).isEmpty();
		assertThat(result.getValue()).hasSize(1)
			.extracting(httpRequest -> mapper.readValue(httpRequest.getBody(), BidRequest.class))
			.flatExtracting(BidRequest::getImp)
			.hasSize(1)
			.first()
			.satisfies(imp -> {
				assertThat(imp.getTagid()).isEqualTo("123");
				assertThat(imp.getSecure()).isEqualTo(1);
				assertThat(imp.getBidfloor()).isEqualTo("2");
				assertThat(imp.getBidfloorcur()).isEqualTo("USD");
			});
	}

	@Test
	public void makeHttpRequestsShouldModifyBannerFormatAndWAndHWhenRequestBannerWAndHIsNull() {
		// given
		final List<Format> formats = Arrays.asList(
			Format.builder().h(20).w(21).build(),
			Format.builder().h(30).w(31).build());

		final BidRequest bidRequest = BidRequest.builder()
			.imp(singletonList(Imp.builder()
				.banner(Banner.builder().format(formats).build())
				.ext(mapper.valueToTree(ExtPrebid.of(null, ExtImpEmxDigital.of("1", "asd"))))
				.build()))
			.id("request_id")
			.build();

		// when
		final Result<List<HttpRequest<BidRequest>>> result = emxDigitalBidder
			.makeHttpRequests(bidRequest);

		// then
		final Banner expectedBanner = Banner.builder().h(20).w(21)
			.format(singletonList(Format.builder().h(30).w(31).build())).build();

		assertThat(result.getErrors()).isEmpty();
		assertThat(result.getValue()).hasSize(1)
			.extracting(httpRequest -> mapper.readValue(httpRequest.getBody(), BidRequest.class))
			.flatExtracting(BidRequest::getImp).hasSize(1)
			.first()
			.satisfies(imp -> {
				assertThat(imp.getTagid()).isEqualTo("1");
				assertThat(imp.getSecure()).isEqualTo(0);
				assertThat(imp.getBidfloor()).isNull();
				assertThat(imp.getBidfloorcur()).isNull();
				assertThat(imp.getBanner()).isEqualTo(expectedBanner);
			});
	}

	@Test
	public void makeHttpRequestsShouldSendRequestToModifiedUrlWithHeaders() {
		// given
		final BidRequest bidRequest = BidRequest.builder()
			.imp(singletonList(Imp.builder()
				.banner(Banner.builder().w(1).h(1).build())
				.ext(mapper.valueToTree(ExtPrebid.of(null, ExtImpEmxDigital.of("1", "asd"))))
				.build()))
			.device(Device.builder().ip("ip").ua("Agent").language("fr").dnt(1).build())
			.site(Site.builder().page("myPage").build())
			.id("request_id")
			.build();

		// when
		final Result<List<HttpRequest<BidRequest>>> result = emxDigitalBidder
			.makeHttpRequests(bidRequest);

		// then
		assertThat(result.getErrors()).isEmpty();
		assertThat(result.getValue()).hasSize(1)
			.first()
			.satisfies(request ->
				assertThat(request.getUri()).startsWith("https://test.endpoint.com?t=1000&ts="));

		assertThat(result.getValue()).hasSize(1)
			.flatExtracting(r -> r.getHeaders().entries())
			.extracting(Map.Entry::getKey, Map.Entry::getValue)
			.containsOnly(
				tuple("Content-Type", "application/json;charset=utf-8"),
				tuple("Accept", "application/json"),
				tuple("User-Agent", "Agent"),
				tuple("X-Forwarded-For", "ip"),
				tuple("Referer", "myPage"),
				tuple("DNT", "1"),
				tuple("Accept-Language", "fr"));
	}

	@Test
	public void makeBidsShouldReturnErrorWhenResponseBodyCouldNotBeParsed() {
		// given
		final HttpCall<BidRequest> httpCall = givenHttpCall(null, "invalid");

		// when
		final Result<List<BidderBid>> result = emxDigitalBidder.makeBids(httpCall, null);

		// then
		assertThat(result.getErrors()).hasSize(1);
		assertThat(result.getErrors().get(0).getMessage())
			.startsWith("Failed to decode: Unrecognized token");
		assertThat(result.getErrors().get(0).getType())
			.isEqualTo(BidderError.Type.bad_server_response);
		assertThat(result.getValue()).isEmpty();
	}

	@Test
	public void makeBidsShouldReturnEmptyListWhenBidResponseIsNull()
		throws JsonProcessingException {
		// given
		final HttpCall<BidRequest> httpCall = givenHttpCall(null,
			mapper.writeValueAsString(null));

		// when
		final Result<List<BidderBid>> result = emxDigitalBidder.makeBids(httpCall, null);

		// then
		assertThat(result.getErrors()).isEmpty();
		assertThat(result.getValue()).isEmpty();
	}

	@Test
	public void makeBidsShouldReturnEmptyListWhenBidResponseSeatBidIsNull()
		throws JsonProcessingException {
		// given
		final HttpCall<BidRequest> httpCall = givenHttpCall(null,
			mapper.writeValueAsString(BidResponse.builder().build()));

		// when
		final Result<List<BidderBid>> result = emxDigitalBidder.makeBids(httpCall, null);

		// then
		assertThat(result.getErrors()).isEmpty();
		assertThat(result.getValue()).isEmpty();
	}

	@Test
	public void makeBidsShouldReturnBannerBidWhenBannerIsPresentWithChangedBidImpId() throws JsonProcessingException {
		// given
		final HttpCall<BidRequest> httpCall = givenHttpCall(
			BidRequest.builder()
				.imp(singletonList(Imp.builder().id("123").build()))
				.build(),
			mapper.writeValueAsString(
				givenBidResponse(bidBuilder -> bidBuilder.id("321").impid("123"))));

		// when
		final Result<List<BidderBid>> result = emxDigitalBidder.makeBids(httpCall, null);

		// then
		assertThat(result.getErrors()).isEmpty();
		assertThat(result.getValue())
			.containsOnly(BidderBid.of(Bid.builder().id("321").impid("321").build(), banner, "USD"));
	}

	@Test
	public void extractTargetingShouldReturnEmptyMap() {
		assertThat(emxDigitalBidder.extractTargeting(mapper.createObjectNode()))
			.isEqualTo(emptyMap());
	}

	private static BidResponse givenBidResponse(
		Function<Bid.BidBuilder, Bid.BidBuilder> bidCustomizer) {
		return BidResponse.builder()
			.seatbid(singletonList(SeatBid.builder()
				.bid(singletonList(bidCustomizer.apply(Bid.builder()).build()))
				.build()))
			.build();
	}

	private static HttpCall<BidRequest> givenHttpCall(BidRequest bidRequest, String body) {
		return HttpCall.success(
			HttpRequest.<BidRequest>builder().payload(bidRequest).build(),
			HttpResponse.of(200, null, body),
			null);
	}
}

