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
import org.prebid.server.privacy.gdpr.vendorlist.proto.Purpose;
import org.prebid.server.privacy.gdpr.vendorlist.proto.SpecialFeature;
import org.prebid.server.privacy.gdpr.vendorlist.proto.SpecialPurpose;
import org.prebid.server.privacy.gdpr.vendorlist.proto.VendorListV2;
import org.prebid.server.privacy.gdpr.vendorlist.proto.VendorV2;
import org.prebid.server.vertx.http.HttpClient;
import org.prebid.server.vertx.http.model.HttpClientResponse;

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
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyZeroInteractions;
import static org.prebid.server.assertion.FutureAssertion.assertThat;
import static org.prebid.server.privacy.gdpr.vendorlist.proto.Purpose.ONE;
import static org.prebid.server.privacy.gdpr.vendorlist.proto.Purpose.TWO;

public class VendorListServiceV2Test extends VertxTest {

    private static final String CACHE_DIR = "/cache/dir";
    private static final long REFRESH_MISSING_LIST_PERIOD_MS = 3600000L;
    private static final String FALLBACK_VENDOR_LIST_PATH = "fallback.json";

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

    private VendorListService<VendorListV2, VendorV2> vendorListService;

    @Before
    public void setUp() throws JsonProcessingException {
        given(fileSystem.existsBlocking(anyString())).willReturn(false); // always create cache dir

        given(bidderCatalog.knownVendorIds()).willReturn(singleton(52));

        given(fileSystem.readFileBlocking(eq(FALLBACK_VENDOR_LIST_PATH)))
                .willReturn(Buffer.buffer(mapper.writeValueAsString(givenVendorList())));

        vendorListService = new VendorListServiceV2(
                CACHE_DIR,
                "http://vendorlist/{VERSION}",
                0,
                REFRESH_MISSING_LIST_PERIOD_MS,
                false,
                null,
                FALLBACK_VENDOR_LIST_PATH,
                bidderCatalog,
                vertx,
                fileSystem,
                httpClient,
                metrics,
                jacksonMapper);
    }

    // Creation related tests

    @Test
    public void creationShouldFailsIfCannotCreateCacheDir() {
        // given
        given(fileSystem.mkdirsBlocking(anyString())).willThrow(new RuntimeException("dir creation error"));

        // then
        assertThatThrownBy(
                () -> new VendorListServiceV2(
                        CACHE_DIR,
                        "http://vendorlist/%s",
                        0,
                        REFRESH_MISSING_LIST_PERIOD_MS,
                        false,
                        null,
                        FALLBACK_VENDOR_LIST_PATH,
                        bidderCatalog,
                        vertx,
                        fileSystem,
                        httpClient,
                        metrics,
                        jacksonMapper))
                .hasMessage("dir creation error");
    }

    @Test
    public void shouldStartUsingFallbackVersionIfDeprecatedIsTrue() {
        // given
        vendorListService = new VendorListServiceV2(
                CACHE_DIR,
                "http://vendorlist/{VERSION}",
                0,
                REFRESH_MISSING_LIST_PERIOD_MS,
                true,
                null,
                FALLBACK_VENDOR_LIST_PATH,
                bidderCatalog,
                vertx,
                fileSystem,
                httpClient,
                metrics,
                jacksonMapper);

        // when
        final Future<Map<Integer, VendorV2>> future = vendorListService.forVersion(1);

        // then
        verifyZeroInteractions(httpClient);
        assertThat(future).succeededWith(singletonMap(
                52, VendorV2.builder()
                        .id(52)
                        .purposes(EnumSet.of(ONE))
                        .legIntPurposes(EnumSet.of(TWO))
                        .flexiblePurposes(EnumSet.noneOf(Purpose.class))
                        .specialPurposes(EnumSet.noneOf(SpecialPurpose.class))
                        .features(EnumSet.noneOf(Feature.class))
                        .specialFeatures(EnumSet.noneOf(SpecialFeature.class))
                        .build()));
    }

    @Test
    public void shouldThrowExceptionIfVersionIsDeprecatedAndNoFallbackPresent() {
        // then
        assertThatThrownBy(() -> new VendorListServiceV2(
                CACHE_DIR,
                "http://vendorlist/{VERSION}",
                0,
                REFRESH_MISSING_LIST_PERIOD_MS,
                true,
                null,
                null,
                bidderCatalog,
                vertx,
                fileSystem,
                httpClient,
                metrics,
                jacksonMapper))
                .isInstanceOf(PreBidException.class)
                .hasMessage("No fallback vendorList for deprecated version present");
    }

    @Test
    public void creationShouldFailsIfCannotReadFiles() {
        // given
        given(fileSystem.readDirBlocking(anyString())).willThrow(new RuntimeException("read error"));

        // then
        assertThatThrownBy(
                () -> new VendorListServiceV2(
                        CACHE_DIR,
                        "http://vendorlist/%s",
                        0,
                        REFRESH_MISSING_LIST_PERIOD_MS,
                        false,
                        null,
                        FALLBACK_VENDOR_LIST_PATH,
                        bidderCatalog,
                        vertx,
                        fileSystem,
                        httpClient,
                        metrics,
                        jacksonMapper))
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
                () -> new VendorListServiceV2(
                        CACHE_DIR,
                        "http://vendorlist/%s",
                        0,
                        REFRESH_MISSING_LIST_PERIOD_MS,
                        false,
                        null,
                        FALLBACK_VENDOR_LIST_PATH,
                        bidderCatalog,
                        vertx,
                        fileSystem,
                        httpClient,
                        metrics,
                        jacksonMapper))
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
                () -> new VendorListServiceV2(
                        CACHE_DIR,
                        "http://vendorlist/%s",
                        0,
                        REFRESH_MISSING_LIST_PERIOD_MS,
                        false,
                        null,
                        FALLBACK_VENDOR_LIST_PATH,
                        bidderCatalog, vertx,
                        fileSystem,
                        httpClient,
                        metrics,
                        jacksonMapper))
                .isInstanceOf(PreBidException.class)
                .hasMessage("Cannot parse vendor list from: invalid");
    }

    // Http related tests

    @Test
    public void shouldPerformHttpRequestWithExpectedQueryIfVendorListNotFound() {
        // given
        givenHttpClientReturnsResponse(200, null);

        // when
        vendorListService.forVersion(1);

        // then
        verify(httpClient).get(eq("http://vendorlist/1"), anyLong());
    }

    @Test
    public void shouldNotAskToSaveFileIfReadingHttpResponseFails() {
        // given
        givenHttpClientProducesException(new RuntimeException("Response exception"));

        // when
        vendorListService.forVersion(1);

        // then
        verify(httpClient).get(anyString(), anyLong());
        verify(fileSystem, never()).writeFile(any(), any(), any());
    }

    @Test
    public void shouldNotAskToSaveFileIfResponseCodeIsNot200() {
        // given
        givenHttpClientReturnsResponse(503, null);

        // when
        vendorListService.forVersion(1);

        // then
        verify(httpClient).get(anyString(), anyLong());
        verify(fileSystem, never()).writeFile(any(), any(), any());
    }

    @Test
    public void shouldNotAskToSaveFileIfResponseBodyCouldNotBeParsed() {
        // given
        givenHttpClientReturnsResponse(200, "response");

        // when
        vendorListService.forVersion(1);

        // then
        verify(httpClient).get(anyString(), anyLong());
        verify(fileSystem, never()).writeFile(any(), any(), any());
    }

    @Test
    public void shouldNotAskToSaveFileIfFetchedVendorListHasInvalidVendorListVersion() throws JsonProcessingException {
        // given
        final VendorListV2 vendorList = VendorListV2.of(null, null, null);
        givenHttpClientReturnsResponse(200, mapper.writeValueAsString(vendorList));

        // when
        vendorListService.forVersion(1);

        // then
        verify(httpClient).get(anyString(), anyLong());
        verify(fileSystem, never()).writeFile(any(), any(), any());
    }

    @Test
    public void shouldNotAskToSaveFileIfFetchedVendorListHasInvalidLastUpdated() throws JsonProcessingException {
        // given
        final VendorListV2 vendorList = VendorListV2.of(1, null, null);
        givenHttpClientReturnsResponse(200, mapper.writeValueAsString(vendorList));

        // when
        vendorListService.forVersion(1);

        // then
        verify(httpClient).get(anyString(), anyLong());
        verify(fileSystem, never()).writeFile(any(), any(), any());
    }

    @Test
    public void shouldNotAskToSaveFileIfFetchedVendorListHasNoVendors() throws JsonProcessingException {
        // given
        final VendorListV2 vendorList = VendorListV2.of(1, new Date(), null);
        givenHttpClientReturnsResponse(200, mapper.writeValueAsString(vendorList));

        // when
        vendorListService.forVersion(1);

        // then
        verify(httpClient).get(anyString(), anyLong());
        verify(fileSystem, never()).writeFile(any(), any(), any());
    }

    @Test
    public void shouldNotAskToSaveFileIfFetchedVendorListHasEmptyVendors() throws JsonProcessingException {
        // given
        final VendorListV2 vendorList = VendorListV2.of(1, new Date(), emptyMap());
        givenHttpClientReturnsResponse(200, mapper.writeValueAsString(vendorList));

        // when
        vendorListService.forVersion(1);

        // then
        verify(httpClient).get(anyString(), anyLong());
        verify(fileSystem, never()).writeFile(any(), any(), any());
    }

    @Test
    public void shouldNotAskToSaveFileIfFetchedVendorListHasAtLeastOneInvalidVendor() throws JsonProcessingException {
        // given
        final VendorListV2 vendorList = VendorListV2.of(1, new Date(), singletonMap(1, VendorV2.builder().build()));
        givenHttpClientReturnsResponse(200, mapper.writeValueAsString(vendorList));

        // when
        vendorListService.forVersion(1);

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
        vendorListService.forVersion(1);

        // then
        verify(fileSystem).writeFile(eq(filePath), eq(Buffer.buffer(vendorListAsString)), any());
    }

    // In-memory cache related tests

    @Test
    public void shouldFailIfVendorListIsBelowOrZero() {
        // given
        givenHttpClientProducesException(new RuntimeException());

        // when
        final Future<?> result1 = vendorListService.forVersion(0);
        final Future<?> result2 = vendorListService.forVersion(-2);

        // then
        assertThat(result1).isFailed().hasMessage("TCF 2 vendor list for version 0 not valid.");
        assertThat(result2).isFailed().hasMessage("TCF 2 vendor list for version -2 not valid.");
    }

    @Test
    public void shouldFailIfVendorListNotFound() {
        // given
        givenHttpClientProducesException(new RuntimeException());

        // when
        final Future<Map<Integer, VendorV2>> future = vendorListService.forVersion(1);

        // then
        assertThat(future).isFailed().hasMessage("TCF 2 vendor list for version 1 not fetched yet, try again later.");
    }

    @Test
    public void shouldReturnVendorListFromCache() throws JsonProcessingException {
        // given
        givenHttpClientReturnsResponse(200, mapper.writeValueAsString(givenVendorList()));

        given(fileSystem.writeFile(anyString(), any(), any()))
                .willAnswer(withSelfAndPassObjectToHandler(Future.succeededFuture()));

        // when
        vendorListService.forVersion(1); // populate cache
        final Future<Map<Integer, VendorV2>> result = vendorListService.forVersion(1);

        // then
        assertThat(result).succeededWith(singletonMap(
                52, VendorV2.builder()
                        .id(52)
                        .purposes(EnumSet.of(ONE))
                        .legIntPurposes(EnumSet.of(TWO))
                        .flexiblePurposes(EnumSet.noneOf(Purpose.class))
                        .specialPurposes(EnumSet.noneOf(SpecialPurpose.class))
                        .features(EnumSet.noneOf(Feature.class))
                        .specialFeatures(EnumSet.noneOf(SpecialFeature.class))
                        .build()));
    }

    @Test
    public void shouldKeepPurposesForAllVendors() throws JsonProcessingException {
        // given
        final VendorV2 firstExternalV2 = VendorV2.builder()
                .id(52)
                .purposes(EnumSet.of(ONE))
                .legIntPurposes(EnumSet.of(TWO))
                .flexiblePurposes(EnumSet.noneOf(Purpose.class))
                .specialPurposes(EnumSet.noneOf(SpecialPurpose.class))
                .features(EnumSet.noneOf(Feature.class))
                .specialFeatures(EnumSet.noneOf(SpecialFeature.class))
                .build();
        final VendorV2 secondExternalV2 = VendorV2.builder()
                .id(42)
                .purposes(EnumSet.of(ONE))
                .legIntPurposes(EnumSet.of(TWO))
                .flexiblePurposes(EnumSet.noneOf(Purpose.class))
                .specialPurposes(EnumSet.noneOf(SpecialPurpose.class))
                .features(EnumSet.noneOf(Feature.class))
                .specialFeatures(EnumSet.noneOf(SpecialFeature.class))
                .build();
        final Map<Integer, VendorV2> idToVendor = new HashMap<>();
        idToVendor.put(52, firstExternalV2);
        idToVendor.put(42, secondExternalV2);

        final VendorListV2 vendorList = VendorListV2.of(1, new Date(), idToVendor);
        givenHttpClientReturnsResponse(200, mapper.writeValueAsString(vendorList));

        given(fileSystem.writeFile(anyString(), any(), any()))
                .willAnswer(withSelfAndPassObjectToHandler(Future.succeededFuture()));

        // when
        vendorListService.forVersion(1); // populate cache
        final Future<Map<Integer, VendorV2>> future = vendorListService.forVersion(1);

        // then
        assertThat(future).succeededWith(idToVendor);
    }

    @Test
    public void shouldReturnFallbackIfVendorListNotFound() {
        // given
        givenHttpClientReturnsResponse(404, StringUtils.EMPTY);

        // when

        // first call triggers http request that results in 404
        final Future<Map<Integer, VendorV2>> future1 = vendorListService.forVersion(1);
        // second call yields fallback vendor list
        final Future<Map<Integer, VendorV2>> future2 = vendorListService.forVersion(1);

        // then
        assertThat(future1).isFailed();
        assertThat(future2).succeededWith(singletonMap(
                52, VendorV2.builder()
                        .id(52)
                        .purposes(EnumSet.of(ONE))
                        .legIntPurposes(EnumSet.of(TWO))
                        .flexiblePurposes(EnumSet.noneOf(Purpose.class))
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
        vendorListService.forVersion(1);

        // then
        verify(metrics).updatePrivacyTcfVendorListMissingMetric(eq(2));
    }

    @Test
    public void shouldIncrementVendorListErrorMetricWhenFileIsNotDownloaded() {
        // given
        givenHttpClientReturnsResponse(503, null);

        // when
        vendorListService.forVersion(1);

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
        vendorListService.forVersion(1);

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
        vendorListService.forVersion(1);

        // then
        verify(metrics).updatePrivacyTcfVendorListOkMetric(eq(2));
    }

    @Test
    public void shouldIncrementVendorListFallbackMetric() {
        // given
        givenHttpClientReturnsResponse(404, StringUtils.EMPTY);

        // when

        // first call triggers http request that results in 404
        vendorListService.forVersion(1);
        // second call yields fallback vendor list
        vendorListService.forVersion(1);

        // then
        verify(metrics).updatePrivacyTcfVendorListFallbackMetric(eq(2));
    }

    private static VendorListV2 givenVendorList() {
        final VendorV2 vendor = VendorV2.builder()
                .id(52)
                .purposes(EnumSet.of(ONE))
                .legIntPurposes(EnumSet.of(TWO))
                .flexiblePurposes(EnumSet.noneOf(Purpose.class))
                .specialPurposes(EnumSet.noneOf(SpecialPurpose.class))
                .features(EnumSet.noneOf(Feature.class))
                .specialFeatures(EnumSet.noneOf(SpecialFeature.class))
                .build();
        return VendorListV2.of(1, new Date(), singletonMap(52, vendor));
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
