package org.prebid.server.bidder.emx_digital;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.iab.openrtb.request.Banner;
import com.iab.openrtb.request.BidRequest;
import com.iab.openrtb.request.Device;
import com.iab.openrtb.request.Format;
import com.iab.openrtb.request.Imp;
import com.iab.openrtb.request.Site;
import com.iab.openrtb.response.Bid;
import com.iab.openrtb.response.BidResponse;
import com.iab.openrtb.response.SeatBid;
import io.vertx.core.MultiMap;
import io.vertx.core.http.HttpMethod;
import io.vertx.core.json.DecodeException;
import io.vertx.core.json.Json;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;
import org.apache.commons.lang3.BooleanUtils;
import org.apache.commons.lang3.StringUtils;
import org.prebid.server.bidder.Bidder;
import org.prebid.server.bidder.model.BidderBid;
import org.prebid.server.bidder.model.BidderError;
import org.prebid.server.bidder.model.HttpCall;
import org.prebid.server.bidder.model.HttpRequest;
import org.prebid.server.bidder.model.Result;
import org.prebid.server.exception.PreBidException;
import org.prebid.server.proto.openrtb.ext.ExtPrebid;
import org.prebid.server.proto.openrtb.ext.request.emx_digital.ExtImpEmxDigital;
import org.prebid.server.proto.openrtb.ext.response.BidType;
import org.prebid.server.util.HttpUtil;

public class EmxDigitalBidder implements Bidder<BidRequest> {

	private static final TypeReference<ExtPrebid<?, ExtImpEmxDigital>> EMX_DIGITAL_EXT_TYPE_REFERENCE = new
		TypeReference<ExtPrebid<?, ExtImpEmxDigital>>() {
		};
	private static final String DEFAULT_BID_CURRENCY = "USD";
	private final String endpointUrl;
	private boolean testing;

	public EmxDigitalBidder(String endpointUrl) {
		this.endpointUrl = HttpUtil.validateUrl(Objects.requireNonNull(endpointUrl));
	}

	@Override
	public Result<List<HttpRequest<BidRequest>>> makeHttpRequests(BidRequest request) {
		final List<BidderError> errors = new ArrayList<>();
		final BidRequest bidRequest;
		try {
			bidRequest = makeBidRequest(request, errors);
		} catch (PreBidException e) {
			errors.add(BidderError.badInput(e.getMessage()));
			return Result.of(Collections.emptyList(), errors);
		}

		final String body = Json.encode(bidRequest);
		final MultiMap headers = makeHeaders(request);
		final String url = makeUrl(request.getTmax());

		return Result.of(Collections.singletonList(
			HttpRequest.<BidRequest>builder()
				.method(HttpMethod.POST)
				.uri(url)
				.body(body)
				.headers(headers)
				.payload(request)
				.build()),
			errors);
	}

	// Handle request errors and formatting to be sent to EMX
	private static BidRequest makeBidRequest(BidRequest request, List<BidderError> errors) {
		final List<BidderError> makeBidRequestErrors = new ArrayList<>();
		final List<Imp> resultImp = new ArrayList<>();
		final boolean isSecure = isSecure(request.getSite());

		for (Imp imp : request.getImp()) {
			try {
				final ExtImpEmxDigital extImpEmxDigital = unpackImpExt(imp);
				final Imp populatedImp = modifyImp(imp, isSecure, extImpEmxDigital);
				resultImp.add(populatedImp);
			} catch (PreBidException e) {
				makeBidRequestErrors.add(BidderError.badInput(e.getMessage()));
			}
		}

		errors.addAll(makeBidRequestErrors);

		if (!makeBidRequestErrors.isEmpty()) {
			throw new PreBidException(
				String.format("Error in makeBidRequest of Imp, err: %s", makeBidRequestErrors));
		}

		return request.toBuilder()
			.imp(resultImp)
			.build();
	}

	private static boolean isSecure(Site site) {
		return site != null && StringUtils.isNotBlank(site.getPage()) && site.getPage()
			.startsWith("https");
	}

	private static ExtImpEmxDigital unpackImpExt(Imp imp) {
		final ExtImpEmxDigital bidder;
		try {
			bidder = Json.mapper.<ExtPrebid<?, ExtImpEmxDigital>>convertValue(imp.getExt(),
				EMX_DIGITAL_EXT_TYPE_REFERENCE).getBidder();
		} catch (IllegalArgumentException e) {
			throw new PreBidException(e.getMessage(), e);
		}

		final int tagidNumber;
		try {
			tagidNumber = Integer.parseInt(bidder.getTagid());
		} catch (NumberFormatException e) {
			throw new PreBidException(
				String.format("tagid must be a String of numbers, ignoring imp id=%s",
					imp.getId()), e);
		}

		if (tagidNumber == 0) {
			throw new PreBidException(String.format("tagid cant be 0, ignoring imp id=%s",
				imp.getId()));
		}

		return bidder;
	}

	private static Imp modifyImp(Imp imp, boolean isSecure, ExtImpEmxDigital extImpEmxDigital) {
		final Banner banner = modifyImpBanner(imp.getBanner());

		final Imp.ImpBuilder impBuilder = imp.toBuilder()
			.tagid(extImpEmxDigital.getTagid())
			.secure(BooleanUtils.toInteger(isSecure))
			.banner(banner);

		final BigDecimal bidfloor;
		try {
			bidfloor = new BigDecimal(extImpEmxDigital.getBidfloor());
		} catch (NumberFormatException | NullPointerException e) {
			return impBuilder.build();
		}

		return impBuilder
			.bidfloor(bidfloor)
			.bidfloorcur("USD")
			.build();
	}

	private static Banner modifyImpBanner(Banner banner) {
		if (banner == null) {
			throw new PreBidException("Request needs to include a Banner object");
		}

		final Banner.BannerBuilder bannerBuilder = banner.toBuilder();

		if (banner.getW() == null && banner.getH() == null) {
			final List<Format> originalFormat = banner.getFormat();

			if (originalFormat == null || originalFormat.isEmpty()) {
				throw new PreBidException("Need at least one size to build request");
			}

			final List<Format> formatSkipFirst = originalFormat.subList(1, originalFormat.size());
			bannerBuilder.format(formatSkipFirst);

			Format firstFormat = originalFormat.get(0);
			bannerBuilder.w(firstFormat.getW());
			bannerBuilder.h(firstFormat.getH());

			return bannerBuilder.build();
		}

		return banner;
	}

	private static MultiMap makeHeaders(BidRequest request) {
		final MultiMap headers = HttpUtil.headers();

		final Device device = request.getDevice();
		if (device != null) {
			HttpUtil.addHeaderIfValueIsNotEmpty(headers, HttpUtil.USER_AGENT_HEADER,
				device.getUa());
			HttpUtil.addHeaderIfValueIsNotEmpty(headers, HttpUtil.X_FORWARDED_FOR_HEADER,
				device.getIp());
			HttpUtil.addHeaderIfValueIsNotEmpty(headers, HttpUtil.ACCEPT_LANGUAGE_HEADER,
				device.getLanguage());
			if (device.getDnt() != null) {
				HttpUtil.addHeaderIfValueIsNotEmpty(headers, HttpUtil.DNT_HEADER,
					String.valueOf(device.getDnt()));
			}
		}

		final Site site = request.getSite();
		if (site != null && StringUtils.isNotBlank(site.getPage())) {
			HttpUtil.addHeaderIfValueIsNotEmpty(headers, HttpUtil.REFERER_HEADER, site.getPage());
		}

		return headers;
	}

	private String makeUrl(Long timeout) {
		final int urlTimeout = timeout == null || timeout == 0 ? 1000 : timeout.intValue();

		if (testing) {
			// for passing validation tests
			return String.format("%s?t=1000&ts=2060541160", endpointUrl);
		}

		return String.format("%s?t=%s&ts=%s&src=pbserver", endpointUrl, urlTimeout,
			(int) Instant.now().getEpochSecond());
	}

	@Override
	public Result<List<BidderBid>> makeBids(HttpCall<BidRequest> httpCall, BidRequest bidRequest) {
		try {
			final BidResponse bidResponse = Json
				.decodeValue(httpCall.getResponse().getBody(), BidResponse.class);
			return Result.of(extractBids(bidResponse), Collections.emptyList());
		} catch (DecodeException | PreBidException e) {
			return Result.emptyWithError(BidderError.badServerResponse(e.getMessage()));
		}
	}

	private static List<BidderBid> extractBids(BidResponse bidResponse) {
		return bidResponse == null || bidResponse.getSeatbid() == null
			? Collections.emptyList()
			: bidsFromResponse(bidResponse);
	}


	private static List<BidderBid> bidsFromResponse(BidResponse bidResponse) {
		return bidResponse.getSeatbid().stream()
			.filter(Objects::nonNull)
			.map(SeatBid::getBid)
			.filter(Objects::nonNull)
			.flatMap(Collection::stream)
			.map(bid -> BidderBid.of(modifyBid(bid), BidType.banner, DEFAULT_BID_CURRENCY))
			.collect(Collectors.toList());
	}

	private static Bid modifyBid(Bid bid) {
		return bid.toBuilder().impid(bid.getId()).build();
	}

	@Override
	public Map<String, String> extractTargeting(ObjectNode ext) {
		return Collections.emptyMap();
	}
}

