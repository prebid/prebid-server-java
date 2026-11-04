package org.prebid.server.bidder.ocm;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.iab.openrtb.request.App;
import com.iab.openrtb.request.BidRequest;
import com.iab.openrtb.request.Dooh;
import com.iab.openrtb.request.Imp;
import com.iab.openrtb.request.Publisher;
import com.iab.openrtb.request.Site;
import com.iab.openrtb.request.User;
import com.iab.openrtb.response.Bid;
import com.iab.openrtb.response.BidResponse;
import com.iab.openrtb.response.SeatBid;
import org.junit.jupiter.api.Test;
import org.prebid.server.VertxTest;
import org.prebid.server.bidder.model.BidderBid;
import org.prebid.server.bidder.model.BidderCall;
import org.prebid.server.bidder.model.BidderError;
import org.prebid.server.bidder.model.HttpRequest;
import org.prebid.server.bidder.model.HttpResponse;
import org.prebid.server.bidder.model.Result;
import org.prebid.server.proto.openrtb.ext.ExtPrebid;
import org.prebid.server.proto.openrtb.ext.request.ExtPublisher;
import org.prebid.server.proto.openrtb.ext.request.ExtPublisherPrebid;
import org.prebid.server.proto.openrtb.ext.request.ExtRequest;
import org.prebid.server.proto.openrtb.ext.request.ExtRequestBidAdjustmentFactors;
import org.prebid.server.proto.openrtb.ext.request.ExtRequestPrebid;
import org.prebid.server.proto.openrtb.ext.request.ExtStoredRequest;
import org.prebid.server.proto.openrtb.ext.request.ocm.ExtImpOcm;
import org.prebid.server.proto.openrtb.ext.response.BidType;
import org.prebid.server.proto.openrtb.ext.response.ExtBidPrebidVideo;

import java.math.BigDecimal;
import java.util.List;
import java.util.function.UnaryOperator;

import static java.util.Arrays.asList;
import static java.util.Collections.singletonList;
import static java.util.function.UnaryOperator.identity;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;
import static org.assertj.core.api.Assertions.tuple;

public class OcmBidderTest extends VertxTest {

    private static final String ENDPOINT_URL = "https://test-endpoint.url/openrtb2/auction";

    private final OcmBidder target = new OcmBidder(ENDPOINT_URL, jacksonMapper);

    @Test
    public void creationShouldFailOnInvalidEndpointUrl() {
        assertThatIllegalArgumentException().isThrownBy(() -> new OcmBidder("invalid_url", jacksonMapper));
    }

    @Test
    public void makeHttpRequestsShouldReturnErrorWhenImpExtCouldNotBeParsed() {
        // given
        final BidRequest bidRequest = givenBidRequest(givenImp(
                imp -> imp.id("imp1").ext(mapper.valueToTree(ExtPrebid.of(null, mapper.createArrayNode())))));

        // when
        final Result<List<HttpRequest<BidRequest>>> result = target.makeHttpRequests(bidRequest);

        // then
        assertThat(result.getErrors()).hasSize(1)
                .allSatisfy(error -> {
                    assertThat(error.getType()).isEqualTo(BidderError.Type.bad_input);
                    assertThat(error.getMessage()).isEqualTo("Error parsing imp.ext for impression imp1");
                });
        assertThat(result.getValue()).isEmpty();
    }

    @Test
    public void makeHttpRequestsShouldReturnErrorWhenPublisherIdIsBlank() {
        // given
        final BidRequest bidRequest = givenBidRequest(
                givenImp(imp -> imp.id("imp1").ext(givenImpExt("   ", "placement-1"))));

        // when
        final Result<List<HttpRequest<BidRequest>>> result = target.makeHttpRequests(bidRequest);

        // then
        assertThat(result.getErrors()).hasSize(1)
                .allSatisfy(error -> {
                    assertThat(error.getType()).isEqualTo(BidderError.Type.bad_input);
                    assertThat(error.getMessage())
                            .isEqualTo("imp imp1: publisherId is required and must not be blank");
                });
        assertThat(result.getValue()).isEmpty();
    }

    @Test
    public void makeHttpRequestsShouldReturnErrorWhenPlacementIdIsBlank() {
        // given
        final BidRequest bidRequest = givenBidRequest(
                givenImp(imp -> imp.id("imp1").ext(givenImpExt("publisher-1", "   "))));

        // when
        final Result<List<HttpRequest<BidRequest>>> result = target.makeHttpRequests(bidRequest);

        // then
        assertThat(result.getErrors()).hasSize(1)
                .allSatisfy(error -> {
                    assertThat(error.getType()).isEqualTo(BidderError.Type.bad_input);
                    assertThat(error.getMessage())
                            .isEqualTo("imp imp1: placementId is required and must not be blank");
                });
        assertThat(result.getValue()).isEmpty();
    }

    @Test
    public void makeHttpRequestsShouldReportPublisherIdFirstWhenBothParamsAreBlank() {
        // given
        final BidRequest bidRequest = givenBidRequest(givenImp(imp -> imp.id("imp1").ext(givenImpExt(null, null))));

        // when
        final Result<List<HttpRequest<BidRequest>>> result = target.makeHttpRequests(bidRequest);

        // then
        assertThat(result.getErrors())
                .extracting(BidderError::getMessage)
                .containsExactly("imp imp1: publisherId is required and must not be blank");
    }

    @Test
    public void makeHttpRequestsShouldReturnErrorAndNoRequestsWhenPublisherIdsDiffer() {
        // given
        final BidRequest bidRequest = givenBidRequest(
                request -> request.site(Site.builder().domain("example.gr").build()),
                givenImp(imp -> imp.id("imp1").ext(givenImpExt("publisher-1", "placement-1"))),
                givenImp(imp -> imp.id("imp2").ext(givenImpExt("publisher-9", "placement-2"))));

        // when
        final Result<List<HttpRequest<BidRequest>>> result = target.makeHttpRequests(bidRequest);

        // then
        assertThat(result.getErrors()).hasSize(1)
                .allSatisfy(error -> {
                    assertThat(error.getType()).isEqualTo(BidderError.Type.bad_input);
                    assertThat(error.getMessage()).isEqualTo("imp imp2: all impressions must share the same"
                            + " publisherId, found \"publisher-9\" and \"publisher-1\"");
                });
        assertThat(result.getValue()).isEmpty();
    }

    @Test
    public void makeHttpRequestsShouldSetStoredRequestIdAndRemoveBidderFromImpExt() {
        // given
        final BidRequest bidRequest = givenBidRequest(
                givenImp(imp -> imp.id("imp1").ext(givenImpExt("publisher-1", "placement-1"))));

        // when
        final Result<List<HttpRequest<BidRequest>>> result = target.makeHttpRequests(bidRequest);

        // then
        assertThat(result.getErrors()).isEmpty();
        assertThat(result.getValue())
                .extracting(HttpRequest::getPayload)
                .flatExtracting(BidRequest::getImp)
                .extracting(Imp::getExt)
                .allSatisfy(ext -> {
                    assertThat(ext.has("bidder")).isFalse();
                    assertThat(ext.get("prebid").get("storedrequest").get("id").textValue())
                            .isEqualTo("placement-1");
                });
    }

    @Test
    public void makeHttpRequestsShouldPreserveUnknownImpExtMembers() {
        // given
        final ObjectNode impExt = givenImpExt("publisher-1", "placement-1");
        impExt.put("gpid", "/1234/homepage#top");
        impExt.put("tid", "b4c9a1de-0000-4000-8000-000000000001");
        impExt.putObject("data").put("pbadslot", "/1234/homepage#top");
        impExt.putObject("prebid").put("is_rewarded_inventory", 1);
        final BidRequest bidRequest = givenBidRequest(givenImp(imp -> imp.id("imp1").ext(impExt)));

        // when
        final Result<List<HttpRequest<BidRequest>>> result = target.makeHttpRequests(bidRequest);

        // then
        assertThat(result.getErrors()).isEmpty();
        assertThat(result.getValue())
                .extracting(HttpRequest::getPayload)
                .flatExtracting(BidRequest::getImp)
                .extracting(Imp::getExt)
                .allSatisfy(ext -> {
                    assertThat(ext.get("gpid").textValue()).isEqualTo("/1234/homepage#top");
                    assertThat(ext.get("tid").textValue()).isEqualTo("b4c9a1de-0000-4000-8000-000000000001");
                    assertThat(ext.get("data").get("pbadslot").textValue()).isEqualTo("/1234/homepage#top");
                    assertThat(ext.get("prebid").get("is_rewarded_inventory").intValue()).isEqualTo(1);
                    assertThat(ext.get("prebid").get("storedrequest").get("id").textValue())
                            .isEqualTo("placement-1");
                });
    }

    @Test
    public void makeHttpRequestsShouldOverwriteExistingStoredRequest() {
        // given
        final ObjectNode impExt = givenImpExt("publisher-1", "placement-1");
        impExt.putObject("prebid").putObject("storedrequest").put("id", "stale");
        final BidRequest bidRequest = givenBidRequest(givenImp(imp -> imp.id("imp1").ext(impExt)));

        // when
        final Result<List<HttpRequest<BidRequest>>> result = target.makeHttpRequests(bidRequest);

        // then
        assertThat(result.getErrors()).isEmpty();
        assertThat(result.getValue())
                .extracting(HttpRequest::getPayload)
                .flatExtracting(BidRequest::getImp)
                .extracting(imp -> imp.getExt().get("prebid").get("storedrequest").get("id").textValue())
                .containsExactly("placement-1");
    }

    @Test
    public void makeHttpRequestsShouldSetSitePublisherIdAndKeepOtherPublisherFields() {
        // given
        final BidRequest bidRequest = givenBidRequest(
                request -> request.site(Site.builder()
                        .domain("example.gr")
                        .publisher(Publisher.builder().id("publisher-from-request").name("Example").build())
                        .build()),
                givenImp(imp -> imp.id("imp1").ext(givenImpExt("publisher-1", "placement-1"))));

        // when
        final Result<List<HttpRequest<BidRequest>>> result = target.makeHttpRequests(bidRequest);

        // then
        assertThat(result.getErrors()).isEmpty();
        assertThat(result.getValue())
                .extracting(HttpRequest::getPayload)
                .extracting(BidRequest::getSite)
                .extracting(Site::getPublisher)
                .extracting(Publisher::getId, Publisher::getName)
                .containsExactly(tuple("publisher-1", "Example"));
    }

    @Test
    public void makeHttpRequestsShouldCreatePublisherWhenSiteHasNone() {
        // given
        final BidRequest bidRequest = givenBidRequest(
                request -> request.site(Site.builder().domain("example.gr").build()),
                givenImp(imp -> imp.id("imp1").ext(givenImpExt("publisher-1", "placement-1"))));

        // when
        final Result<List<HttpRequest<BidRequest>>> result = target.makeHttpRequests(bidRequest);

        // then
        assertThat(result.getErrors()).isEmpty();
        assertThat(result.getValue())
                .extracting(HttpRequest::getPayload)
                .extracting(payload -> payload.getSite().getPublisher())
                .extracting(Publisher::getId, Publisher::getExt)
                .containsExactly(tuple("publisher-1", null));
    }

    @Test
    public void makeHttpRequestsShouldRemoveParentAccountAndKeepOtherPublisherExtProperties() {
        // given
        final ExtPublisher publisherExt = ExtPublisher.of(ExtPublisherPrebid.of("origin-host"));
        publisherExt.addProperty("unrelated", mapper.createObjectNode().put("value", 1));
        final BidRequest bidRequest = givenBidRequest(
                request -> request.site(Site.builder()
                        .publisher(Publisher.builder().id("publisher-from-request").ext(publisherExt).build())
                        .build()),
                givenImp(imp -> imp.id("imp1").ext(givenImpExt("publisher-1", "placement-1"))));

        // when
        final Result<List<HttpRequest<BidRequest>>> result = target.makeHttpRequests(bidRequest);

        // then
        assertThat(result.getErrors()).isEmpty();
        assertThat(result.getValue())
                .extracting(HttpRequest::getPayload)
                .extracting(payload -> payload.getSite().getPublisher().getExt())
                .allSatisfy(ext -> {
                    assertThat(ext.getPrebid()).isNull();
                    assertThat(ext.getProperty("unrelated").get("value").intValue()).isEqualTo(1);
                });
    }

    @Test
    public void makeHttpRequestsShouldDropPublisherExtWhenOnlyParentAccountPresent() {
        // given
        final BidRequest bidRequest = givenBidRequest(
                request -> request.site(Site.builder()
                        .publisher(Publisher.builder()
                                .ext(ExtPublisher.of(ExtPublisherPrebid.of("origin-host")))
                                .build())
                        .build()),
                givenImp(imp -> imp.id("imp1").ext(givenImpExt("publisher-1", "placement-1"))));

        // when
        final Result<List<HttpRequest<BidRequest>>> result = target.makeHttpRequests(bidRequest);

        // then
        assertThat(result.getErrors()).isEmpty();
        assertThat(result.getValue())
                .extracting(HttpRequest::getPayload)
                .extracting(payload -> payload.getSite().getPublisher().getExt())
                .containsOnlyNulls();
    }

    @Test
    public void makeHttpRequestsShouldSetAppPublisherIdWhenSiteIsAbsent() {
        // given
        final BidRequest bidRequest = givenBidRequest(
                request -> request.app(App.builder().bundle("com.example.app").build()),
                givenImp(imp -> imp.id("imp1").ext(givenImpExt("publisher-1", "placement-1"))));

        // when
        final Result<List<HttpRequest<BidRequest>>> result = target.makeHttpRequests(bidRequest);

        // then
        assertThat(result.getErrors()).isEmpty();
        assertThat(result.getValue())
                .extracting(HttpRequest::getPayload)
                .extracting(payload -> payload.getApp().getPublisher().getId())
                .containsExactly("publisher-1");
    }

    @Test
    public void makeHttpRequestsShouldSetDoohPublisherIdAndRemoveParentAccount() {
        // given
        final BidRequest bidRequest = givenBidRequest(
                request -> request.dooh(Dooh.builder()
                        .name("billboard")
                        .publisher(Publisher.builder()
                                .id("host-publisher")
                                .ext(ExtPublisher.of(ExtPublisherPrebid.of("host-account")))
                                .build())
                        .build()),
                givenImp(imp -> imp.id("imp1").ext(givenImpExt("publisher-1", "placement-1"))));

        // when
        final Result<List<HttpRequest<BidRequest>>> result = target.makeHttpRequests(bidRequest);

        // then
        assertThat(result.getErrors()).isEmpty();
        assertThat(result.getValue())
                .extracting(HttpRequest::getPayload)
                .extracting(BidRequest::getDooh)
                .containsExactly(Dooh.builder()
                        .name("billboard")
                        .publisher(Publisher.builder().id("publisher-1").build())
                        .build());
    }

    @Test
    public void makeHttpRequestsShouldSetPublisherIdOnEveryChannelPresent() {
        // given
        final Publisher hostPublisher = Publisher.builder()
                .id("host-publisher")
                .ext(ExtPublisher.of(ExtPublisherPrebid.of("host-account")))
                .build();
        final BidRequest bidRequest = givenBidRequest(
                request -> request
                        .site(Site.builder().domain("example.gr").publisher(hostPublisher).build())
                        .app(App.builder().bundle("com.example.app").publisher(hostPublisher).build())
                        .dooh(Dooh.builder().name("billboard").publisher(hostPublisher).build()),
                givenImp(imp -> imp.id("imp1").ext(givenImpExt("publisher-1", "placement-1"))));

        // when
        final Result<List<HttpRequest<BidRequest>>> result = target.makeHttpRequests(bidRequest);

        // then
        final Publisher expectedPublisher = Publisher.builder().id("publisher-1").build();
        assertThat(result.getErrors()).isEmpty();
        assertThat(result.getValue())
                .extracting(HttpRequest::getPayload)
                .extracting(
                        payload -> payload.getSite().getPublisher(),
                        payload -> payload.getApp().getPublisher(),
                        payload -> payload.getDooh().getPublisher())
                .containsExactly(tuple(expectedPublisher, expectedPublisher, expectedPublisher));
    }

    @Test
    public void makeHttpRequestsShouldRemoveRequestLevelStoredRequest() {
        // given
        final BidRequest bidRequest = givenBidRequest(
                request -> request.ext(ExtRequest.of(ExtRequestPrebid.builder()
                        .storedrequest(ExtStoredRequest.of("host-stored-request"))
                        .debug(1)
                        .build())),
                givenImp(imp -> imp.id("imp1").ext(givenImpExt("publisher-1", "placement-1"))));

        // when
        final Result<List<HttpRequest<BidRequest>>> result = target.makeHttpRequests(bidRequest);

        // then
        assertThat(result.getErrors()).isEmpty();
        assertThat(result.getValue())
                .extracting(HttpRequest::getPayload)
                .extracting(payload -> payload.getExt().getPrebid())
                .extracting(ExtRequestPrebid::getStoredrequest, ExtRequestPrebid::getDebug)
                .containsExactly(tuple(null, 1));
    }

    @Test
    public void makeHttpRequestsShouldPreserveRequestExtPropertiesWhenRemovingStoredRequest() {
        // given
        final ExtRequest requestExt = ExtRequest.of(ExtRequestPrebid.builder()
                .storedrequest(ExtStoredRequest.of("host-stored-request"))
                .build());
        requestExt.addProperty("unrelated", mapper.createObjectNode().put("value", 1));
        final BidRequest bidRequest = givenBidRequest(
                request -> request.ext(requestExt),
                givenImp(imp -> imp.id("imp1").ext(givenImpExt("publisher-1", "placement-1"))));

        // when
        final Result<List<HttpRequest<BidRequest>>> result = target.makeHttpRequests(bidRequest);

        // then
        assertThat(result.getErrors()).isEmpty();
        assertThat(result.getValue())
                .extracting(HttpRequest::getPayload)
                .extracting(BidRequest::getExt)
                .allSatisfy(ext -> {
                    assertThat(ext.getPrebid().getStoredrequest()).isNull();
                    assertThat(ext.getProperty("unrelated").get("value").intValue()).isEqualTo(1);
                });
    }

    @Test
    public void makeHttpRequestsShouldRemoveRequestLevelProfiles() {
        // given
        final BidRequest bidRequest = givenBidRequest(
                request -> request.ext(ExtRequest.of(ExtRequestPrebid.builder()
                        .profiles(asList("host-profile-1", "host-profile-2"))
                        .debug(1)
                        .build())),
                givenImp(imp -> imp.id("imp1").ext(givenImpExt("publisher-1", "placement-1"))));

        // when
        final Result<List<HttpRequest<BidRequest>>> result = target.makeHttpRequests(bidRequest);

        // then
        assertThat(result.getErrors()).isEmpty();
        assertThat(result.getValue())
                .extracting(HttpRequest::getPayload)
                .extracting(payload -> payload.getExt().getPrebid())
                .extracting(ExtRequestPrebid::getProfiles, ExtRequestPrebid::getDebug)
                .containsExactly(tuple(null, 1));
    }

    @Test
    public void makeHttpRequestsShouldRemoveGlobalBidderParams() {
        // given
        final ObjectNode bidderParams = mapper.createObjectNode();
        bidderParams.putObject("ocm").put("publisherId", "publisher-1");
        final BidRequest bidRequest = givenBidRequest(
                request -> request.ext(ExtRequest.of(ExtRequestPrebid.builder()
                        .bidderparams(bidderParams)
                        .debug(1)
                        .build())),
                givenImp(imp -> imp.id("imp1").ext(givenImpExt("publisher-1", "placement-1"))));

        // when
        final Result<List<HttpRequest<BidRequest>>> result = target.makeHttpRequests(bidRequest);

        // then
        assertThat(result.getErrors()).isEmpty();
        assertThat(result.getValue())
                .extracting(HttpRequest::getPayload)
                .extracting(payload -> payload.getExt().getPrebid())
                .extracting(ExtRequestPrebid::getBidderparams, ExtRequestPrebid::getDebug)
                .containsExactly(tuple(null, 1));
    }

    @Test
    public void makeHttpRequestsShouldRemoveBidAdjustmentFactors() {
        // given
        final ExtRequestBidAdjustmentFactors bidAdjustmentFactors = ExtRequestBidAdjustmentFactors.builder().build();
        bidAdjustmentFactors.addFactor("myocm", BigDecimal.valueOf(0.8));
        final BidRequest bidRequest = givenBidRequest(
                request -> request.ext(ExtRequest.of(ExtRequestPrebid.builder()
                        .bidadjustmentfactors(bidAdjustmentFactors)
                        .debug(1)
                        .build())),
                givenImp(imp -> imp.id("imp1").ext(givenImpExt("publisher-1", "placement-1"))));

        // when
        final Result<List<HttpRequest<BidRequest>>> result = target.makeHttpRequests(bidRequest);

        // then
        assertThat(result.getErrors()).isEmpty();
        assertThat(result.getValue())
                .extracting(HttpRequest::getPayload)
                .extracting(payload -> payload.getExt().getPrebid())
                .extracting(ExtRequestPrebid::getBidadjustmentfactors, ExtRequestPrebid::getDebug)
                .containsExactly(tuple(null, 1));
    }

    @Test
    public void makeHttpRequestsShouldRemoveBidAdjustments() {
        // given
        final ObjectNode bidAdjustments = mapper.createObjectNode();
        bidAdjustments.putObject("mediatype").putObject("banner").putArray("myocm");
        final BidRequest bidRequest = givenBidRequest(
                request -> request.ext(ExtRequest.of(ExtRequestPrebid.builder()
                        .bidadjustments(bidAdjustments)
                        .debug(1)
                        .build())),
                givenImp(imp -> imp.id("imp1").ext(givenImpExt("publisher-1", "placement-1"))));

        // when
        final Result<List<HttpRequest<BidRequest>>> result = target.makeHttpRequests(bidRequest);

        // then
        assertThat(result.getErrors()).isEmpty();
        assertThat(result.getValue())
                .extracting(HttpRequest::getPayload)
                .extracting(payload -> payload.getExt().getPrebid())
                .extracting(ExtRequestPrebid::getBidadjustments, ExtRequestPrebid::getDebug)
                .containsExactly(tuple(null, 1));
    }

    @Test
    public void makeHttpRequestsShouldKeepRequestExtWhenItHasNoHostOnlyData() {
        // given
        final ExtRequest requestExt = ExtRequest.of(ExtRequestPrebid.builder().debug(1).build());
        final BidRequest bidRequest = givenBidRequest(
                request -> request.ext(requestExt),
                givenImp(imp -> imp.id("imp1").ext(givenImpExt("publisher-1", "placement-1"))));

        // when
        final Result<List<HttpRequest<BidRequest>>> result = target.makeHttpRequests(bidRequest);

        // then
        assertThat(result.getErrors()).isEmpty();
        assertThat(result.getValue())
                .extracting(HttpRequest::getPayload)
                .extracting(BidRequest::getExt)
                .containsExactly(requestExt);
    }

    @Test
    public void makeHttpRequestsShouldTolerateAbsentRequestExt() {
        // given
        final BidRequest bidRequest = givenBidRequest(
                givenImp(imp -> imp.id("imp1").ext(givenImpExt("publisher-1", "placement-1"))));

        // when
        final Result<List<HttpRequest<BidRequest>>> result = target.makeHttpRequests(bidRequest);

        // then
        assertThat(result.getErrors()).isEmpty();
        assertThat(result.getValue())
                .extracting(HttpRequest::getPayload)
                .extracting(BidRequest::getExt)
                .containsOnlyNulls();
    }

    @Test
    public void makeHttpRequestsShouldMoveBuyeruidToUserIdAndKeepOtherUserFields() {
        // given
        final BidRequest bidRequest = givenBidRequest(
                request -> request.user(User.builder()
                        .id("host-user-id")
                        .buyeruid("ocm-uid")
                        .keywords("sports")
                        .build()),
                givenImp(imp -> imp.id("imp1").ext(givenImpExt("publisher-1", "placement-1"))));

        // when
        final Result<List<HttpRequest<BidRequest>>> result = target.makeHttpRequests(bidRequest);

        // then
        assertThat(result.getErrors()).isEmpty();
        assertThat(result.getValue())
                .extracting(HttpRequest::getPayload)
                .extracting(BidRequest::getUser)
                .containsExactly(User.builder().id("ocm-uid").keywords("sports").build());
    }

    @Test
    public void makeHttpRequestsShouldKeepUserWhenBuyeruidIsBlank() {
        // given
        final User user = User.builder().id("host-user-id").buyeruid("  ").build();
        final BidRequest bidRequest = givenBidRequest(
                request -> request.user(user),
                givenImp(imp -> imp.id("imp1").ext(givenImpExt("publisher-1", "placement-1"))));

        // when
        final Result<List<HttpRequest<BidRequest>>> result = target.makeHttpRequests(bidRequest);

        // then
        assertThat(result.getErrors()).isEmpty();
        assertThat(result.getValue())
                .extracting(HttpRequest::getPayload)
                .extracting(BidRequest::getUser)
                .containsExactly(user);
    }

    @Test
    public void makeHttpRequestsShouldTolerateAbsentUser() {
        // given
        final BidRequest bidRequest = givenBidRequest(
                givenImp(imp -> imp.id("imp1").ext(givenImpExt("publisher-1", "placement-1"))));

        // when
        final Result<List<HttpRequest<BidRequest>>> result = target.makeHttpRequests(bidRequest);

        // then
        assertThat(result.getErrors()).isEmpty();
        assertThat(result.getValue())
                .extracting(HttpRequest::getPayload)
                .extracting(BidRequest::getUser)
                .containsOnlyNulls();
    }

    @Test
    public void makeHttpRequestsShouldNotMutateIncomingRequest() {
        // given
        final Publisher publisher = Publisher.builder().id("publisher-from-request").build();
        final ObjectNode impExt = givenImpExt("publisher-1", "placement-1");
        final ExtRequest requestExt = ExtRequest.of(ExtRequestPrebid.builder()
                .storedrequest(ExtStoredRequest.of("host-stored-request"))
                .build());
        final User user = User.builder().id("host-user-id").buyeruid("ocm-uid").build();
        final BidRequest bidRequest = givenBidRequest(
                request -> request.site(Site.builder().publisher(publisher).build())
                        .user(user)
                        .ext(requestExt)
                        .tmax(1000L),
                givenImp(imp -> imp.id("imp1").ext(impExt)));

        // when
        target.makeHttpRequests(bidRequest);

        // then
        assertThat(bidRequest.getTmax()).isEqualTo(1000L);
        assertThat(bidRequest.getUser()).isSameAs(user);
        assertThat(user.getId()).isEqualTo("host-user-id");
        assertThat(user.getBuyeruid()).isEqualTo("ocm-uid");
        assertThat(bidRequest.getSite().getPublisher().getId()).isEqualTo("publisher-from-request");
        assertThat(publisher.getId()).isEqualTo("publisher-from-request");
        assertThat(bidRequest.getImp().getFirst().getExt().has("bidder")).isTrue();
        assertThat(bidRequest.getImp().getFirst().getExt().has("prebid")).isFalse();
        assertThat(requestExt.getPrebid().getStoredrequest()).isEqualTo(ExtStoredRequest.of("host-stored-request"));
    }

    @Test
    public void makeHttpRequestsShouldReturnSingleRequestWithAllImpIds() {
        // given
        final BidRequest bidRequest = givenBidRequest(
                request -> request.site(Site.builder().domain("example.gr").build()),
                givenImp(imp -> imp.id("imp1").ext(givenImpExt("publisher-1", "placement-1"))),
                givenImp(imp -> imp.id("imp2").ext(givenImpExt("publisher-1", "placement-2"))));

        // when
        final Result<List<HttpRequest<BidRequest>>> result = target.makeHttpRequests(bidRequest);

        // then
        assertThat(result.getErrors()).isEmpty();
        assertThat(result.getValue()).hasSize(1);
        assertThat(result.getValue())
                .flatExtracting(HttpRequest::getImpIds)
                .containsExactlyInAnyOrder("imp1", "imp2");
        assertThat(result.getValue())
                .extracting(HttpRequest::getUri)
                .containsExactly(ENDPOINT_URL);
    }

    @Test
    public void makeHttpRequestsShouldDeductDefaultBufferFromTmax() {
        // given
        final BidRequest bidRequest = givenBidRequest(
                request -> request.tmax(1000L),
                givenImp(imp -> imp.id("imp1").ext(givenImpExt("publisher-1", "placement-1"))));

        // when
        final Result<List<HttpRequest<BidRequest>>> result = target.makeHttpRequests(bidRequest);

        // then
        assertThat(result.getErrors()).isEmpty();
        assertThat(result.getValue())
                .extracting(HttpRequest::getPayload)
                .extracting(BidRequest::getTmax)
                .containsExactly(750L);
    }

    @Test
    public void makeHttpRequestsShouldDeductCustomBufferFromTmax() {
        // given
        final BidRequest bidRequest = givenBidRequest(
                request -> request.tmax(1000L),
                givenImp(imp -> imp.id("imp1").ext(givenImpExt("publisher-1", "placement-1", 400))));

        // when
        final Result<List<HttpRequest<BidRequest>>> result = target.makeHttpRequests(bidRequest);

        // then
        assertThat(result.getErrors()).isEmpty();
        assertThat(result.getValue())
                .extracting(HttpRequest::getPayload)
                .extracting(BidRequest::getTmax)
                .containsExactly(600L);
    }

    @Test
    public void makeHttpRequestsShouldNotChangeTmaxWhenBufferIsZero() {
        // given
        final BidRequest bidRequest = givenBidRequest(
                request -> request.tmax(1000L),
                givenImp(imp -> imp.id("imp1").ext(givenImpExt("publisher-1", "placement-1", 0))));

        // when
        final Result<List<HttpRequest<BidRequest>>> result = target.makeHttpRequests(bidRequest);

        // then
        assertThat(result.getErrors()).isEmpty();
        assertThat(result.getValue())
                .extracting(HttpRequest::getPayload)
                .extracting(BidRequest::getTmax)
                .containsExactly(1000L);
    }

    @Test
    public void makeHttpRequestsShouldUseLargestBufferAcrossImps() {
        // given
        final BidRequest bidRequest = givenBidRequest(
                request -> request.tmax(1000L),
                givenImp(imp -> imp.id("imp1").ext(givenImpExt("publisher-1", "placement-1", 100))),
                givenImp(imp -> imp.id("imp2").ext(givenImpExt("publisher-1", "placement-2"))),
                givenImp(imp -> imp.id("imp3").ext(givenImpExt("publisher-1", "placement-3", 300))));

        // when
        final Result<List<HttpRequest<BidRequest>>> result = target.makeHttpRequests(bidRequest);

        // then
        assertThat(result.getErrors()).isEmpty();
        assertThat(result.getValue())
                .extracting(HttpRequest::getPayload)
                .extracting(BidRequest::getTmax)
                .containsExactly(700L);
    }

    @Test
    public void makeHttpRequestsShouldIgnoreNegativeBuffer() {
        // given
        final BidRequest bidRequest = givenBidRequest(
                request -> request.tmax(1000L),
                givenImp(imp -> imp.id("imp1").ext(givenImpExt("publisher-1", "placement-1", -100))));

        // when
        final Result<List<HttpRequest<BidRequest>>> result = target.makeHttpRequests(bidRequest);

        // then
        assertThat(result.getErrors()).isEmpty();
        assertThat(result.getValue())
                .extracting(HttpRequest::getPayload)
                .extracting(BidRequest::getTmax)
                .containsExactly(750L);
    }

    @Test
    public void makeHttpRequestsShouldNotReduceTmaxBelowBuffer() {
        // given
        final BidRequest bidRequest = givenBidRequest(
                request -> request.tmax(300L),
                givenImp(imp -> imp.id("imp1").ext(givenImpExt("publisher-1", "placement-1"))));

        // when
        final Result<List<HttpRequest<BidRequest>>> result = target.makeHttpRequests(bidRequest);

        // then
        assertThat(result.getErrors()).isEmpty();
        assertThat(result.getValue())
                .extracting(HttpRequest::getPayload)
                .extracting(BidRequest::getTmax)
                .containsExactly(250L);
    }

    @Test
    public void makeHttpRequestsShouldKeepTmaxWhenSmallerThanBuffer() {
        // given
        final BidRequest bidRequest = givenBidRequest(
                request -> request.tmax(200L),
                givenImp(imp -> imp.id("imp1").ext(givenImpExt("publisher-1", "placement-1"))));

        // when
        final Result<List<HttpRequest<BidRequest>>> result = target.makeHttpRequests(bidRequest);

        // then
        assertThat(result.getErrors()).isEmpty();
        assertThat(result.getValue())
                .extracting(HttpRequest::getPayload)
                .extracting(BidRequest::getTmax)
                .containsExactly(200L);
    }

    @Test
    public void makeHttpRequestsShouldKeepTmaxWhenAbsent() {
        // given
        final BidRequest bidRequest = givenBidRequest(
                givenImp(imp -> imp.id("imp1").ext(givenImpExt("publisher-1", "placement-1"))));

        // when
        final Result<List<HttpRequest<BidRequest>>> result = target.makeHttpRequests(bidRequest);

        // then
        assertThat(result.getErrors()).isEmpty();
        assertThat(result.getValue())
                .extracting(HttpRequest::getPayload)
                .extracting(BidRequest::getTmax)
                .containsNull();
    }

    @Test
    public void makeHttpRequestsShouldKeepTmaxWhenZero() {
        // given
        final BidRequest bidRequest = givenBidRequest(
                request -> request.tmax(0L),
                givenImp(imp -> imp.id("imp1").ext(givenImpExt("publisher-1", "placement-1"))));

        // when
        final Result<List<HttpRequest<BidRequest>>> result = target.makeHttpRequests(bidRequest);

        // then
        assertThat(result.getErrors()).isEmpty();
        assertThat(result.getValue())
                .extracting(HttpRequest::getPayload)
                .extracting(BidRequest::getTmax)
                .containsExactly(0L);
    }

    @Test
    public void makeBidsShouldReturnEmptyResultForEmptyBody() {
        // given
        final BidderCall<BidRequest> httpCall = givenHttpCallWithBody("");

        // when
        final Result<List<BidderBid>> result = target.makeBids(httpCall, null);

        // then
        assertThat(result.getErrors()).isEmpty();
        assertThat(result.getValue()).isEmpty();
    }

    @Test
    public void makeBidsShouldReturnEmptyResultForEmptyJsonObjectBody() {
        // given
        final BidderCall<BidRequest> httpCall = givenHttpCallWithBody("{}");

        // when
        final Result<List<BidderBid>> result = target.makeBids(httpCall, null);

        // then
        assertThat(result.getErrors()).isEmpty();
        assertThat(result.getValue()).isEmpty();
    }

    @Test
    public void makeBidsShouldReturnErrorWhenResponseBodyCouldNotBeParsed() {
        // given
        final BidderCall<BidRequest> httpCall = givenHttpCallWithBody("invalid_json");

        // when
        final Result<List<BidderBid>> result = target.makeBids(httpCall, null);

        // then
        assertThat(result.getErrors()).hasSize(1)
                .allSatisfy(error -> assertThat(error.getType()).isEqualTo(BidderError.Type.bad_server_response));
        assertThat(result.getValue()).isEmpty();
    }

    @Test
    public void makeBidsShouldReturnEmptyResultWhenSeatBidIsEmpty() throws JsonProcessingException {
        // given
        final BidderCall<BidRequest> httpCall = givenHttpCall(
                BidResponse.builder().cur("USD").seatbid(List.of()).build());

        // when
        final Result<List<BidderBid>> result = target.makeBids(httpCall, null);

        // then
        assertThat(result.getErrors()).isEmpty();
        assertThat(result.getValue()).isEmpty();
    }

    @Test
    public void makeBidsShouldUseResponseCurrency() throws JsonProcessingException {
        // given
        final BidderCall<BidRequest> httpCall = givenHttpCall("EUR", givenBid(bid -> bid.mtype(1)));

        // when
        final Result<List<BidderBid>> result = target.makeBids(httpCall, null);

        // then
        assertThat(result.getErrors()).isEmpty();
        assertThat(result.getValue())
                .extracting(BidderBid::getBidCurrency)
                .containsExactly("EUR");
    }

    @Test
    public void makeBidsShouldDefaultCurrencyToUsd() throws JsonProcessingException {
        // given
        final BidderCall<BidRequest> httpCall = givenHttpCall(null, givenBid(bid -> bid.mtype(1)));

        // when
        final Result<List<BidderBid>> result = target.makeBids(httpCall, null);

        // then
        assertThat(result.getErrors()).isEmpty();
        assertThat(result.getValue())
                .extracting(BidderBid::getBidCurrency)
                .containsExactly("USD");
    }

    @Test
    public void makeBidsShouldReturnBannerBidWhenMtypeIsOne() throws JsonProcessingException {
        // given
        final BidderCall<BidRequest> httpCall = givenHttpCall("USD", givenBid(bid -> bid.mtype(1)));

        // when
        final Result<List<BidderBid>> result = target.makeBids(httpCall, null);

        // then
        assertThat(result.getErrors()).isEmpty();
        assertThat(result.getValue())
                .extracting(BidderBid::getType)
                .containsExactly(BidType.banner);
    }

    @Test
    public void makeBidsShouldReturnVideoBidWhenMtypeIsTwo() throws JsonProcessingException {
        // given
        final BidderCall<BidRequest> httpCall = givenHttpCall("USD", givenBid(bid -> bid.mtype(2)));

        // when
        final Result<List<BidderBid>> result = target.makeBids(httpCall, null);

        // then
        assertThat(result.getErrors()).isEmpty();
        assertThat(result.getValue())
                .extracting(BidderBid::getType)
                .containsExactly(BidType.video);
    }

    @Test
    public void makeBidsShouldReturnNativeBidWhenMtypeIsFour() throws JsonProcessingException {
        // given
        final BidderCall<BidRequest> httpCall = givenHttpCall("USD", givenBid(bid -> bid.mtype(4)));

        // when
        final Result<List<BidderBid>> result = target.makeBids(httpCall, null);

        // then
        assertThat(result.getErrors()).isEmpty();
        assertThat(result.getValue())
                .extracting(BidderBid::getType)
                .containsExactly(BidType.xNative);
    }

    @Test
    public void makeBidsShouldPreferMtypeOverExtType() throws JsonProcessingException {
        // given
        final BidderCall<BidRequest> httpCall = givenHttpCall("USD",
                givenBid(bid -> bid.mtype(1).ext(givenBidExtWithType("video"))));

        // when
        final Result<List<BidderBid>> result = target.makeBids(httpCall, null);

        // then
        assertThat(result.getErrors()).isEmpty();
        assertThat(result.getValue())
                .extracting(BidderBid::getType)
                .containsExactly(BidType.banner);
    }

    @Test
    public void makeBidsShouldReturnErrorWhenMtypeIsUnsupportedEvenIfExtTypeIsSupported() throws
            JsonProcessingException {
        // given
        final BidderCall<BidRequest> httpCall = givenHttpCall("USD",
                givenBid(bid -> bid.mtype(3).ext(givenBidExtWithType("banner"))));

        // when
        final Result<List<BidderBid>> result = target.makeBids(httpCall, null);

        // then
        assertThat(result.getErrors()).hasSize(1)
                .allSatisfy(error -> {
                    assertThat(error.getType()).isEqualTo(BidderError.Type.bad_server_response);
                    assertThat(error.getMessage()).isEqualTo("unsupported mtype 3 for bid \"bid-1\"");
                });
        assertThat(result.getValue()).isEmpty();
    }

    @Test
    public void makeBidsShouldResolveBannerTypeFromExtWhenMtypeIsAbsent() throws JsonProcessingException {
        // given
        final BidderCall<BidRequest> httpCall = givenHttpCall("USD",
                givenBid(bid -> bid.ext(givenBidExtWithType("banner"))));

        // when
        final Result<List<BidderBid>> result = target.makeBids(httpCall, null);

        // then
        assertThat(result.getErrors()).isEmpty();
        assertThat(result.getValue())
                .extracting(BidderBid::getType)
                .containsExactly(BidType.banner);
    }

    @Test
    public void makeBidsShouldResolveVideoTypeFromExtWhenMtypeIsAbsent() throws JsonProcessingException {
        // given
        final BidderCall<BidRequest> httpCall = givenHttpCall("USD",
                givenBid(bid -> bid.ext(givenBidExtWithType("video"))));

        // when
        final Result<List<BidderBid>> result = target.makeBids(httpCall, null);

        // then
        assertThat(result.getErrors()).isEmpty();
        assertThat(result.getValue())
                .extracting(BidderBid::getType)
                .containsExactly(BidType.video);
    }

    @Test
    public void makeBidsShouldResolveNativeTypeFromExtWhenMtypeIsAbsent() throws JsonProcessingException {
        // given
        final BidderCall<BidRequest> httpCall = givenHttpCall("USD",
                givenBid(bid -> bid.ext(givenBidExtWithType("native"))));

        // when
        final Result<List<BidderBid>> result = target.makeBids(httpCall, null);

        // then
        assertThat(result.getErrors()).isEmpty();
        assertThat(result.getValue())
                .extracting(BidderBid::getType)
                .containsExactly(BidType.xNative);
    }

    @Test
    public void makeBidsShouldReturnErrorWhenMtypeIsAbsentAndExtTypeIsAudio() throws JsonProcessingException {
        // given
        final BidderCall<BidRequest> httpCall = givenHttpCall("USD",
                givenBid(bid -> bid.ext(givenBidExtWithType("audio"))));

        // when
        final Result<List<BidderBid>> result = target.makeBids(httpCall, null);

        // then
        assertThat(result.getErrors()).hasSize(1)
                .allSatisfy(error -> {
                    assertThat(error.getType()).isEqualTo(BidderError.Type.bad_server_response);
                    assertThat(error.getMessage()).isEqualTo(
                            "unable to determine media type for bid \"bid-1\": no mtype and no ext.prebid.type");
                });
        assertThat(result.getValue()).isEmpty();
    }

    @Test
    public void makeBidsShouldReturnErrorWhenMtypeIsAbsentAndBidExtIsMissing() throws JsonProcessingException {
        // given
        final BidderCall<BidRequest> httpCall = givenHttpCall("USD", givenBid(identity()));

        // when
        final Result<List<BidderBid>> result = target.makeBids(httpCall, null);

        // then
        assertThat(result.getErrors())
                .extracting(BidderError::getMessage)
                .containsExactly(
                        "unable to determine media type for bid \"bid-1\": no mtype and no ext.prebid.type");
        assertThat(result.getValue()).isEmpty();
    }

    @Test
    public void makeBidsShouldSkipOnlyBidsWithUnresolvableMediaType() throws JsonProcessingException {
        // given
        final BidderCall<BidRequest> httpCall = givenHttpCall("USD",
                givenBid(bid -> bid.id("bid-1").mtype(1)),
                givenBid(bid -> bid.id("bid-2")));

        // when
        final Result<List<BidderBid>> result = target.makeBids(httpCall, null);

        // then
        assertThat(result.getErrors())
                .extracting(BidderError::getMessage)
                .containsExactly(
                        "unable to determine media type for bid \"bid-2\": no mtype and no ext.prebid.type");
        assertThat(result.getValue())
                .extracting(bidderBid -> bidderBid.getBid().getId())
                .containsExactly("bid-1");
    }

    @Test
    public void makeBidsShouldSkipOnlyBidsWithUnsupportedMtype() throws JsonProcessingException {
        // given
        final BidderCall<BidRequest> httpCall = givenHttpCall("USD",
                givenBid(bid -> bid.id("bid-banner-1").mtype(1)),
                givenBid(bid -> bid.id("bid-audio-1").mtype(3)));

        // when
        final Result<List<BidderBid>> result = target.makeBids(httpCall, null);

        // then
        assertThat(result.getErrors())
                .extracting(BidderError::getType, BidderError::getMessage)
                .containsExactly(tuple(
                        BidderError.Type.bad_server_response,
                        "unsupported mtype 3 for bid \"bid-audio-1\""));
        assertThat(result.getValue())
                .extracting(bidderBid -> bidderBid.getBid().getId(), BidderBid::getType)
                .containsExactly(tuple("bid-banner-1", BidType.banner));
    }

    @Test
    public void makeBidsShouldPopulateVideoInfoFromBidExt() throws JsonProcessingException {
        // given
        final ObjectNode bidExt = givenBidExtWithType("video");
        ((ObjectNode) bidExt.get("prebid")).putObject("video")
                .put("duration", 30)
                .put("primary_category", "IAB1-2");
        final BidderCall<BidRequest> httpCall = givenHttpCall("USD", givenBid(bid -> bid.mtype(2).ext(bidExt)));

        // when
        final Result<List<BidderBid>> result = target.makeBids(httpCall, null);

        // then
        assertThat(result.getErrors()).isEmpty();
        assertThat(result.getValue())
                .extracting(BidderBid::getVideoInfo)
                .containsExactly(ExtBidPrebidVideo.of(30, "IAB1-2"));
    }

    @Test
    public void makeBidsShouldPopulateVideoInfoWhenTypeResolvesToVideoFromExt() throws JsonProcessingException {
        // given
        final ObjectNode bidExt = givenBidExtWithType("video");
        ((ObjectNode) bidExt.get("prebid")).putObject("video")
                .put("duration", 15)
                .put("primary_category", "IAB1-7");
        final BidderCall<BidRequest> httpCall = givenHttpCall("USD", givenBid(bid -> bid.ext(bidExt)));

        // when
        final Result<List<BidderBid>> result = target.makeBids(httpCall, null);

        // then
        assertThat(result.getErrors()).isEmpty();
        assertThat(result.getValue())
                .extracting(BidderBid::getVideoInfo)
                .containsExactly(ExtBidPrebidVideo.of(15, "IAB1-7"));
    }

    @Test
    public void makeBidsShouldNotPopulateVideoInfoForNonVideoBid() throws JsonProcessingException {
        // given
        final ObjectNode bidExt = givenBidExtWithType("video");
        ((ObjectNode) bidExt.get("prebid")).putObject("video")
                .put("duration", 30)
                .put("primary_category", "IAB1-2");
        final BidderCall<BidRequest> httpCall = givenHttpCall("USD", givenBid(bid -> bid.mtype(1).ext(bidExt)));

        // when
        final Result<List<BidderBid>> result = target.makeBids(httpCall, null);

        // then
        assertThat(result.getErrors()).isEmpty();
        assertThat(result.getValue())
                .extracting(BidderBid::getType, BidderBid::getVideoInfo)
                .containsExactly(tuple(BidType.banner, null));
    }

    @Test
    public void makeBidsShouldNotPopulateVideoInfoWhenVideoBidExtHasNone() throws JsonProcessingException {
        // given
        final BidderCall<BidRequest> httpCall = givenHttpCall("USD", givenBid(bid -> bid.mtype(2)));

        // when
        final Result<List<BidderBid>> result = target.makeBids(httpCall, null);

        // then
        assertThat(result.getErrors()).isEmpty();
        assertThat(result.getValue())
                .extracting(BidderBid::getVideoInfo)
                .containsOnlyNulls();
    }

    @Test
    public void makeBidsShouldResolveTypeFromExtWhenMtypeIsZero() throws JsonProcessingException {
        // given
        final BidderCall<BidRequest> httpCall = givenHttpCall("USD",
                givenBid(bid -> bid.mtype(0).ext(givenBidExtWithType("banner"))));

        // when
        final Result<List<BidderBid>> result = target.makeBids(httpCall, null);

        // then
        assertThat(result.getErrors()).isEmpty();
        assertThat(result.getValue())
                .extracting(BidderBid::getType)
                .containsExactly(BidType.banner);
    }

    @Test
    public void makeBidsShouldReturnErrorWhenBidExtPrebidIsNotAnObject() throws JsonProcessingException {
        // given
        final ObjectNode bidExt = mapper.createObjectNode();
        bidExt.put("prebid", "not-an-object");
        final BidderCall<BidRequest> httpCall = givenHttpCall("USD", givenBid(bid -> bid.ext(bidExt)));

        // when
        final Result<List<BidderBid>> result = target.makeBids(httpCall, null);

        // then
        assertThat(result.getErrors())
                .extracting(BidderError::getMessage)
                .containsExactly(
                        "unable to determine media type for bid \"bid-1\": no mtype and no ext.prebid.type");
        assertThat(result.getValue()).isEmpty();
    }

    @Test
    public void makeHttpRequestsShouldReturnErrorWhenImpExtIsAbsent() {
        // given
        final BidRequest bidRequest = givenBidRequest(givenImp(imp -> imp.id("imp1")));

        // when
        final Result<List<HttpRequest<BidRequest>>> result = target.makeHttpRequests(bidRequest);

        // then
        assertThat(result.getErrors()).hasSize(1)
                .allSatisfy(error -> {
                    assertThat(error.getType()).isEqualTo(BidderError.Type.bad_input);
                    assertThat(error.getMessage()).isEqualTo("Error parsing imp.ext for impression imp1");
                });
        assertThat(result.getValue()).isEmpty();
    }

    @Test
    public void makeHttpRequestsShouldKeepPublisherExtWhenItHasNoPrebid() {
        // given
        final ExtPublisher publisherExt = ExtPublisher.empty();
        publisherExt.addProperty("unrelated", mapper.createObjectNode().put("value", 1));
        final BidRequest bidRequest = givenBidRequest(
                request -> request.site(Site.builder()
                        .publisher(Publisher.builder().ext(publisherExt).build())
                        .build()),
                givenImp(imp -> imp.id("imp1").ext(givenImpExt("publisher-1", "placement-1"))));

        // when
        final Result<List<HttpRequest<BidRequest>>> result = target.makeHttpRequests(bidRequest);

        // then
        assertThat(result.getErrors()).isEmpty();
        assertThat(result.getValue())
                .extracting(HttpRequest::getPayload)
                .extracting(payload -> payload.getSite().getPublisher().getExt())
                .containsExactly(publisherExt);
    }

    private static BidRequest givenBidRequest(UnaryOperator<BidRequest.BidRequestBuilder> bidRequestCustomizer,
                                              Imp... imps) {

        return bidRequestCustomizer.apply(BidRequest.builder().imp(asList(imps))).build();
    }

    private static BidRequest givenBidRequest(Imp... imps) {
        return givenBidRequest(identity(), imps);
    }

    private static Imp givenImp(UnaryOperator<Imp.ImpBuilder> impCustomizer) {
        return impCustomizer.apply(Imp.builder()).build();
    }

    private static ObjectNode givenImpExt(String publisherId, String placementId) {
        return givenImpExt(publisherId, placementId, null);
    }

    private static ObjectNode givenImpExt(String publisherId, String placementId, Integer tmaxBufferMs) {
        return mapper.valueToTree(ExtPrebid.of(null, ExtImpOcm.of(publisherId, placementId, tmaxBufferMs)));
    }

    private static Bid givenBid(UnaryOperator<Bid.BidBuilder> bidCustomizer) {
        return bidCustomizer.apply(Bid.builder()
                        .id("bid-1")
                        .impid("imp1")
                        .price(BigDecimal.valueOf(1.85)))
                .build();
    }

    private static ObjectNode givenBidExtWithType(String type) {
        final ObjectNode bidExt = mapper.createObjectNode();
        bidExt.putObject("prebid").put("type", type);
        return bidExt;
    }

    private static BidderCall<BidRequest> givenHttpCallWithBody(String body) {
        return BidderCall.succeededHttp(
                HttpRequest.<BidRequest>builder().build(),
                HttpResponse.of(200, null, body),
                null);
    }

    private static BidderCall<BidRequest> givenHttpCall(BidResponse bidResponse) throws JsonProcessingException {
        return givenHttpCallWithBody(mapper.writeValueAsString(bidResponse));
    }

    private static BidderCall<BidRequest> givenHttpCall(String currency, Bid... bids)
            throws JsonProcessingException {

        return givenHttpCall(BidResponse.builder()
                .cur(currency)
                .seatbid(singletonList(SeatBid.builder().seat("ocm").bid(asList(bids)).build()))
                .build());
    }
}
