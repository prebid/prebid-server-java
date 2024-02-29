package org.prebid.server.privacy.gdpr.vendorlist;

import com.fasterxml.jackson.core.JsonProcessingException;
import io.vertx.core.Future;
import io.vertx.core.Handler;
import io.vertx.core.Vertx;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.file.FileSystem;
import org.apache.commons.lang3.StringUtils;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnit;
import org.mockito.junit.MockitoRule;
import org.mockito.stubbing.Answer;
import org.prebid.server.VertxTest;
import org.prebid.server.bidder.BidderCatalog;
import org.prebid.server.exception.PreBidException;
import org.prebid.server.metric.Metrics;
import org.prebid.server.privacy.gdpr.vendorlist.proto.Feature;
import org.prebid.server.privacy.gdpr.vendorlist.proto.PurposeCode;
import org.prebid.server.privacy.gdpr.vendorlist.proto.SpecialFeature;
import org.prebid.server.privacy.gdpr.vendorlist.proto.SpecialPurpose;
import org.prebid.server.privacy.gdpr.vendorlist.proto.Vendor;
import org.prebid.server.privacy.gdpr.vendorlist.proto.VendorList;
import org.prebid.server.vertx.httpclient.HttpClient;
import org.prebid.server.vertx.httpclient.model.HttpClientResponse;

import java.io.File;
import java.util.Date;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.Map;

import static java.util.Collections.emptyMap;
import static java.util.Collections.singleton;
import static java.util.Collections.singletonList;
import static java.util.Collections.singletonMap;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.prebid.server.assertion.FutureAssertion.assertThat;
import static org.prebid.server.privacy.gdpr.vendorlist.proto.PurposeCode.ONE;
import static org.prebid.server.privacy.gdpr.vendorlist.proto.PurposeCode.TWO;

public class VendorListServiceTest extends VertxTest {

    private static final String CACHE_DIR = "/cache/dir";
    private static final long REFRESH_MISSING_LIST_PERIOD_MS = 3600000L;
    private static final String FALLBACK_VENDOR_LIST_PATH = "fallback.json";
    private static final String GENERATION_VERSION = "v0";

    @Rule
    public final MockitoRule mockitoRule = MockitoJUnit.rule();

    @Mock
    private Vertx vertx;
    @Mock
    private FileSystem fileSystem;
    @Mock
    private HttpClient httpClient;
    @Mock
    private Metrics metrics;
    @Mock
    private BidderCatalog bidderCatalog;
    @Mock
    private VendorListFetchThrottler fetchThrottler;

    private VendorListService target;

    @Before
    public void setUp() throws JsonProcessingException {
        given(fileSystem.existsBlocking(anyString())).willReturn(false); // always create cache dir

        given(bidderCatalog.knownVendorIds()).willReturn(singleton(52));

        given(fileSystem.readFileBlocking(eq(FALLBACK_VENDOR_LIST_PATH)))
                .willReturn(Buffer.buffer(mapper.writeValueAsString(givenVendorList())));

        given(fetchThrottler.registerFetchAttempt(anyInt())).willReturn(true);

        target = new VendorListService(
                0,
                CACHE_DIR,
                "http://vendorlist/{VERSION}",
                0,
                REFRESH_MISSING_LIST_PERIOD_MS,
                false,
                FALLBACK_VENDOR_LIST_PATH,
                vertx,
                fileSystem,
                httpClient,
                metrics,
                GENERATION_VERSION,
                jacksonMapper,
                fetchThrottler);
    }

    // Creation related tests

    @Test
    public void creationShouldFailsIfCannotCreateCacheDir() {
        // given
        given(fileSystem.mkdirsBlocking(anyString())).willThrow(new RuntimeException("dir creation error"));

        // then
        assertThatThrownBy(
                () -> new VendorListService(
                        0,
                        CACHE_DIR,
                        "http://vendorlist/%s",
                        0,
                        REFRESH_MISSING_LIST_PERIOD_MS,
                        false,
                        FALLBACK_VENDOR_LIST_PATH,
                        vertx,
                        fileSystem,
                        httpClient,
                        metrics,
                        GENERATION_VERSION,
                        jacksonMapper,
                        fetchThrottler))
                .hasMessage("dir creation error");
    }

    @Test
    public void shouldStartUsingFallbackVersionIfDeprecatedIsTrue() {
        // given
        target = new VendorListService(
                0,
                CACHE_DIR,
                "http://vendorlist/{VERSION}",
                0,
                REFRESH_MISSING_LIST_PERIOD_MS,
                true,
                FALLBACK_VENDOR_LIST_PATH,
                vertx,
                fileSystem,
                httpClient,
                metrics,
                GENERATION_VERSION,
                jacksonMapper,
                fetchThrottler);

        // when
        final Future<Map<Integer, Vendor>> future = target.forVersion(1);

        // then
        verifyNoInteractions(httpClient);
        assertThat(future).succeededWith(singletonMap(
                52, Vendor.builder()
                        .id(52)
                        .purposes(EnumSet.of(ONE))
                        .legIntPurposes(EnumSet.of(TWO))
                        .flexiblePurposes(EnumSet.noneOf(PurposeCode.class))
                        .specialPurposes(EnumSet.noneOf(SpecialPurpose.class))
                        .features(EnumSet.noneOf(Feature.class))
                        .specialFeatures(EnumSet.noneOf(SpecialFeature.class))
                        .build()));
    }

    @Test
    public void shouldThrowExceptionIfVersionIsDeprecatedAndNoFallbackPresent() {
        // then
        assertThatThrownBy(() -> new VendorListService(
                0,
                CACHE_DIR,
                "http://vendorlist/{VERSION}",
                0,
                REFRESH_MISSING_LIST_PERIOD_MS,
                true,
                null,
                vertx,
                fileSystem,
                httpClient,
                metrics,
                GENERATION_VERSION,
                jacksonMapper,
                fetchThrottler))
                .isInstanceOf(PreBidException.class)
                .hasMessage("No fallback vendorList for deprecated version present");
    }

    @Test
    public void creationShouldFailsIfCannotReadFiles() {
        // given
        given(fileSystem.readDirBlocking(anyString())).willThrow(new RuntimeException("read error"));

        // then
        assertThatThrownBy(
                () -> new VendorListService(
                        0,
                        CACHE_DIR,
                        "http://vendorlist/%s",
                        0,
                        REFRESH_MISSING_LIST_PERIOD_MS,
                        false,
                        FALLBACK_VENDOR_LIST_PATH,
                        vertx,
                        fileSystem,
                        httpClient,
                        metrics,
                        GENERATION_VERSION,
                        jacksonMapper,
                        fetchThrottler))
                .isInstanceOf(RuntimeException.class)
                .hasMessage("read error");
    }

    @Test
    public void creationShouldFailsIfCannotReadAtLeastOneVendorListFile() {
        // given
        given(fileSystem.readDirBlocking(anyString())).willReturn(singletonList("1.json"));
        given(fileSystem.readFileBlocking(anyString())).willThrow(new RuntimeException("read error"));

        // then
        assertThatThrownBy(
                () -> new VendorListService(
                        0,
                        CACHE_DIR,
                        "http://vendorlist/%s",
                        0,
                        REFRESH_MISSING_LIST_PERIOD_MS,
                        false,
                        FALLBACK_VENDOR_LIST_PATH,
                        vertx,
                        fileSystem,
                        httpClient,
                        metrics,
                        GENERATION_VERSION,
                        jacksonMapper,
                        fetchThrottler))
                .isInstanceOf(RuntimeException.class)
                .hasMessage("read error");
    }

    @Test
    public void creationShouldFailsIfAtLeastOneVendorListFileCannotBeParsed() {
        // given
        given(fileSystem.readDirBlocking(anyString())).willReturn(singletonList("1.json"));
        given(fileSystem.readFileBlocking(anyString())).willReturn(Buffer.buffer("invalid"));

        // then
        assertThatThrownBy(
                () -> new VendorListService(
                        0,
                        CACHE_DIR,
                        "http://vendorlist/%s",
                        0,
                        REFRESH_MISSING_LIST_PERIOD_MS,
                        false,
                        FALLBACK_VENDOR_LIST_PATH,
                        vertx,
                        fileSystem,
                        httpClient,
                        metrics,
                        GENERATION_VERSION,
                        jacksonMapper,
                        fetchThrottler))
                .isInstanceOf(PreBidException.class)
                .hasMessage("Cannot parse vendor list from: invalid");
    }

    // Http related tests

    @Test
    public void shouldPerformHttpRequestWithExpectedQueryIfVendorListNotFoundAndFetchAllowed() {
        // given
        givenHttpClientReturnsResponse(200, null);
        given(fetchThrottler.registerFetchAttempt(1)).willReturn(true);

        // when
        target.forVersion(1);

        // then
        verify(httpClient).get(eq("http://vendorlist/1"), anyLong());
    }

    @Test
    public void shouldNotPerformHttpRequestIfVendorListNotFoundAndFetchNotAllowed() {
        // given
        givenHttpClientReturnsResponse(200, null);
        given(fetchThrottler.registerFetchAttempt(1)).willReturn(false);

        // when
        target.forVersion(1);

        // then
        verify(httpClient, never()).get(anyString(), anyLong());
        verify(fileSystem, never()).writeFile(any(), any(), any());
    }

    @Test
    public void shouldNotAskToSaveFileIfReadingHttpResponseFails() {
        // given
        givenHttpClientProducesException(new RuntimeException("Response exception"));

        // when
        target.forVersion(1);

        // then
        verify(httpClient).get(anyString(), anyLong());
        verify(fileSystem, never()).writeFile(any(), any(), any());
    }

    @Test
    public void shouldNotAskToSaveFileIfResponseCodeIsNot200() {
        // given
        givenHttpClientReturnsResponse(503, null);

        // when
        target.forVersion(1);

        // then
        verify(httpClient).get(anyString(), anyLong());
        verify(fileSystem, never()).writeFile(any(), any(), any());
    }

    @Test
    public void shouldNotAskToSaveFileIfResponseBodyCouldNotBeParsed() {
        // given
        givenHttpClientReturnsResponse(200, "response");

        // when
        target.forVersion(1);

        // then
        verify(httpClient).get(anyString(), anyLong());
        verify(fileSystem, never()).writeFile(any(), any(), any());
    }

    @Test
    public void shouldNotAskToSaveFileIfFetchedVendorListHasInvalidVendorListVersion() throws JsonProcessingException {
        // given
        final VendorList vendorList = VendorList.of(null, null, null);
        givenHttpClientReturnsResponse(200, mapper.writeValueAsString(vendorList));

        // when
        target.forVersion(1);

        // then
        verify(httpClient).get(anyString(), anyLong());
        verify(fileSystem, never()).writeFile(any(), any(), any());
    }

    @Test
    public void shouldNotAskToSaveFileIfFetchedVendorListHasInvalidLastUpdated() throws JsonProcessingException {
        // given
        final VendorList vendorList = VendorList.of(1, null, null);
        givenHttpClientReturnsResponse(200, mapper.writeValueAsString(vendorList));

        // when
        target.forVersion(1);

        // then
        verify(httpClient).get(anyString(), anyLong());
        verify(fileSystem, never()).writeFile(any(), any(), any());
    }

    @Test
    public void shouldNotAskToSaveFileIfFetchedVendorListHasNoVendors() throws JsonProcessingException {
        // given
        final VendorList vendorList = VendorList.of(1, new Date(), null);
        givenHttpClientReturnsResponse(200, mapper.writeValueAsString(vendorList));

        // when
        target.forVersion(1);

        // then
        verify(httpClient).get(anyString(), anyLong());
        verify(fileSystem, never()).writeFile(any(), any(), any());
    }

    @Test
    public void shouldNotAskToSaveFileIfFetchedVendorListHasEmptyVendors() throws JsonProcessingException {
        // given
        final VendorList vendorList = VendorList.of(1, new Date(), emptyMap());
        givenHttpClientReturnsResponse(200, mapper.writeValueAsString(vendorList));

        // when
        target.forVersion(1);

        // then
        verify(httpClient).get(anyString(), anyLong());
        verify(fileSystem, never()).writeFile(any(), any(), any());
    }

    @Test
    public void shouldNotAskToSaveFileIfFetchedVendorListHasAtLeastOneInvalidVendor() throws JsonProcessingException {
        // given
        final VendorList vendorList = VendorList.of(1, new Date(), singletonMap(1, Vendor.builder().build()));
        givenHttpClientReturnsResponse(200, mapper.writeValueAsString(vendorList));

        // when
        target.forVersion(1);

        // then
        verify(httpClient).get(anyString(), anyLong());
        verify(fileSystem, never()).writeFile(any(), any(), any());
    }

    // File system related tests

    @Test
    public void shouldSaveFileWithExpectedPathAndContentIfVendorListNotFound() throws JsonProcessingException {
        // given
        final String vendorListAsString = mapper.writeValueAsString(givenVendorList());
        givenHttpClientReturnsResponse(200, vendorListAsString);
        // generate file path to avoid conflicts with path separators in different OS
        final String filePath = new File("/cache/dir/1.json").getPath();

        // when
        target.forVersion(1);

        // then
        verify(fileSystem).writeFile(eq(filePath), eq(Buffer.buffer(vendorListAsString)), any());
    }

    // In-memory cache related tests

    @Test
    public void shouldFailIfVendorListIsBelowOrZero() {
        // given
        givenHttpClientProducesException(new RuntimeException());

        // when
        final Future<?> result1 = target.forVersion(0);
        final Future<?> result2 = target.forVersion(-2);

        // then
        assertThat(result1).isFailed().hasMessage("TCF 2 vendor list for version v0.0 not valid.");
        assertThat(result2).isFailed().hasMessage("TCF 2 vendor list for version v0.-2 not valid.");
    }

    @Test
    public void shouldFailIfVendorListNotFound() {
        // given
        givenHttpClientProducesException(new RuntimeException());

        // when
        final Future<Map<Integer, Vendor>> future = target.forVersion(1);

        // then
        assertThat(future).isFailed()
                .hasMessage("TCF 2 vendor list for version v0.1 not fetched yet, try again later.");
    }

    @Test
    public void shouldReturnVendorListFromCache() throws JsonProcessingException {
        // given
        givenHttpClientReturnsResponse(200, mapper.writeValueAsString(givenVendorList()));

        given(fileSystem.writeFile(anyString(), any(), any()))
                .willAnswer(withSelfAndPassObjectToHandler(Future.succeededFuture()));

        // when
        target.forVersion(1); // populate cache
        final Future<Map<Integer, Vendor>> result = target.forVersion(1);

        // then
        assertThat(result).succeededWith(singletonMap(
                52, Vendor.builder()
                        .id(52)
                        .purposes(EnumSet.of(ONE))
                        .legIntPurposes(EnumSet.of(TWO))
                        .flexiblePurposes(EnumSet.noneOf(PurposeCode.class))
                        .specialPurposes(EnumSet.noneOf(SpecialPurpose.class))
                        .features(EnumSet.noneOf(Feature.class))
                        .specialFeatures(EnumSet.noneOf(SpecialFeature.class))
                        .build()));
    }

    @Test
    public void shouldKeepPurposesForAllVendors() throws JsonProcessingException {
        // given
        final Vendor firstExternalV2 = Vendor.builder()
                .id(52)
                .purposes(EnumSet.of(ONE))
                .legIntPurposes(EnumSet.of(TWO))
                .flexiblePurposes(EnumSet.noneOf(PurposeCode.class))
                .specialPurposes(EnumSet.noneOf(SpecialPurpose.class))
                .features(EnumSet.noneOf(Feature.class))
                .specialFeatures(EnumSet.noneOf(SpecialFeature.class))
                .build();
        final Vendor secondExternalV2 = Vendor.builder()
                .id(42)
                .purposes(EnumSet.of(ONE))
                .legIntPurposes(EnumSet.of(TWO))
                .flexiblePurposes(EnumSet.noneOf(PurposeCode.class))
                .specialPurposes(EnumSet.noneOf(SpecialPurpose.class))
                .features(EnumSet.noneOf(Feature.class))
                .specialFeatures(EnumSet.noneOf(SpecialFeature.class))
                .build();
        final Map<Integer, Vendor> idToVendor = new HashMap<>();
        idToVendor.put(52, firstExternalV2);
        idToVendor.put(42, secondExternalV2);

        final VendorList vendorList = VendorList.of(1, new Date(), idToVendor);
        givenHttpClientReturnsResponse(200, mapper.writeValueAsString(vendorList));

        given(fileSystem.writeFile(anyString(), any(), any()))
                .willAnswer(withSelfAndPassObjectToHandler(Future.succeededFuture()));

        // when
        target.forVersion(1); // populate cache
        final Future<Map<Integer, Vendor>> future = target.forVersion(1);

        // then
        assertThat(future).succeededWith(idToVendor);
    }

    @Test
    public void shouldReturnFallbackIfVendorListNotFound() {
        // given
        givenHttpClientReturnsResponse(404, StringUtils.EMPTY);

        // when

        // first call triggers http request that results in 404
        final Future<Map<Integer, Vendor>> future1 = target.forVersion(1);
        // second call yields fallback vendor list
        final Future<Map<Integer, Vendor>> future2 = target.forVersion(1);

        // then
        assertThat(future1).isFailed();
        assertThat(future2).succeededWith(singletonMap(
                52, Vendor.builder()
                        .id(52)
                        .purposes(EnumSet.of(ONE))
                        .legIntPurposes(EnumSet.of(TWO))
                        .flexiblePurposes(EnumSet.noneOf(PurposeCode.class))
                        .specialPurposes(EnumSet.noneOf(SpecialPurpose.class))
                        .features(EnumSet.noneOf(Feature.class))
                        .specialFeatures(EnumSet.noneOf(SpecialFeature.class))
                        .build()));
    }

    // Metrics tests

    @Test
    public void shouldIncrementVendorListMissingMetric() {
        // given
        givenHttpClientReturnsResponse(200, null);

        // when
        target.forVersion(1);

        // then
        verify(metrics).updatePrivacyTcfVendorListMissingMetric(eq(2));
    }

    @Test
    public void shouldIncrementVendorListErrorMetricWhenFileIsNotDownloaded() {
        // given
        givenHttpClientReturnsResponse(503, null);

        // when
        target.forVersion(1);

        // then
        verify(metrics).updatePrivacyTcfVendorListErrorMetric(eq(2));
    }

    @Test
    public void shouldIncrementVendorListErrorMetricWhenFileIsNotSaved() throws JsonProcessingException {
        // given
        givenHttpClientReturnsResponse(200, mapper.writeValueAsString(givenVendorList()));

        given(fileSystem.writeFile(anyString(), any(), any()))
                .willAnswer(withSelfAndPassObjectToHandler(Future.failedFuture("error")));

        // when
        target.forVersion(1);

        // then
        verify(metrics).updatePrivacyTcfVendorListErrorMetric(eq(2));
    }

    @Test
    public void shouldIncrementVendorListOkMetric() throws JsonProcessingException {
        // given
        givenHttpClientReturnsResponse(200, mapper.writeValueAsString(givenVendorList()));

        given(fileSystem.writeFile(anyString(), any(), any()))
                .willAnswer(withSelfAndPassObjectToHandler(Future.succeededFuture()));

        // when
        target.forVersion(1);

        // then
        verify(metrics).updatePrivacyTcfVendorListOkMetric(eq(2));
    }

    @Test
    public void shouldIncrementVendorListFallbackMetric() {
        // given
        givenHttpClientReturnsResponse(404, StringUtils.EMPTY);

        // when

        // first call triggers http request that results in 404
        target.forVersion(1);
        // second call yields fallback vendor list
        target.forVersion(1);

        // then
        verify(metrics).updatePrivacyTcfVendorListFallbackMetric(eq(2));
    }

    private static VendorList givenVendorList() {
        final Vendor vendor = Vendor.builder()
                .id(52)
                .purposes(EnumSet.of(ONE))
                .legIntPurposes(EnumSet.of(TWO))
                .flexiblePurposes(EnumSet.noneOf(PurposeCode.class))
                .specialPurposes(EnumSet.noneOf(SpecialPurpose.class))
                .features(EnumSet.noneOf(Feature.class))
                .specialFeatures(EnumSet.noneOf(SpecialFeature.class))
                .build();
        return VendorList.of(1, new Date(), singletonMap(52, vendor));
    }

    private void givenHttpClientReturnsResponse(int statusCode, String response) {
        given(httpClient.get(anyString(), anyLong()))
                .willReturn(Future.succeededFuture(HttpClientResponse.of(statusCode, null, response)));
    }

    private void givenHttpClientProducesException(Throwable throwable) {
        given(httpClient.get(anyString(), anyLong()))
                .willReturn(Future.failedFuture(throwable));
    }

    @SuppressWarnings("unchecked")
    private static <T> Answer<Object> withSelfAndPassObjectToHandler(T obj) {
        return inv -> {
            // invoking handler right away passing mock to it
            ((Handler<T>) inv.getArgument(2)).handle(obj);
            return inv.getMock();
        };
    }
}
