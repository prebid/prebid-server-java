package org.prebid.server.privacy.gdpr.vendorlist;

import com.fasterxml.jackson.core.JsonProcessingException;
import io.vertx.core.Future;
import io.vertx.core.Promise;
import io.vertx.core.Vertx;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.prebid.server.VertxTest;
import org.prebid.server.metric.Metrics;
import org.prebid.server.privacy.gdpr.vendorlist.proto.Feature;
import org.prebid.server.privacy.gdpr.vendorlist.proto.PurposeCode;
import org.prebid.server.privacy.gdpr.vendorlist.proto.SpecialFeature;
import org.prebid.server.privacy.gdpr.vendorlist.proto.SpecialPurpose;
import org.prebid.server.privacy.gdpr.vendorlist.proto.Vendor;
import org.prebid.server.privacy.gdpr.vendorlist.proto.VendorList;
import org.prebid.server.vertx.httpclient.HttpClient;
import org.prebid.server.vertx.httpclient.model.HttpClientResponse;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Arrays;
import java.util.Date;
import java.util.EnumSet;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.prebid.server.privacy.gdpr.vendorlist.proto.PurposeCode.ONE;
import static org.prebid.server.privacy.gdpr.vendorlist.proto.PurposeCode.TWO;

@ExtendWith(MockitoExtension.class)
public class LiveVendorListServiceTest extends VertxTest {

    private static final Instant NOW = Instant.parse("2024-06-01T12:00:00Z");
    private static final String CACHE_DIR = "/cache/dir";
    private static final String LIVE_GVL_URL = "https://example.com";
    private static final long REFRESH_PERIOD_MS = 1000;

    @Mock
    private Vertx vertx;
    @Mock
    private HttpClient httpClient;
    @Mock
    private VendorListFileStore vendorListFileStore;
    @Mock
    private Metrics metrics;

    private LiveVendorListService target;

    @BeforeEach
    public void setUp() {
        target = new LiveVendorListService(
                CACHE_DIR,
                LIVE_GVL_URL,
                REFRESH_PERIOD_MS,
                1000,
                vertx,
                httpClient,
                vendorListFileStore,
                metrics,
                jacksonMapper,
                Clock.fixed(NOW, ZoneOffset.UTC));
    }

    @Test
    public void isDeletedShouldReturnFalseWhenFetchNeverSucceeded() {
        // when and then
        assertThat(target.isDeleted(1)).isFalse();
        assertThat(target.isDeleted(null)).isFalse();
    }

    @Test
    public void isDeletedShouldReturnTrueWhenVendorIsDeletedInLiveVendorList() throws JsonProcessingException {
        // given
        final Vendor vendor = givenVendor(42, "2024-01-01T00:00:00Z");
        final String responseBody = mapper.writeValueAsString(givenVendorList(vendor));
        given(httpClient.get(anyString(), anyLong()))
                .willReturn(Future.succeededFuture(HttpClientResponse.of(200, null, responseBody)));

        // when
        target.refresh();

        // then
        assertThat(target.isDeleted(42)).isTrue();
        assertThat(target.isDeleted(99)).isFalse();
    }

    @Test
    public void extractDeletedVendorIdsShouldReturnOnlyVendorsWithPastDeletedDate() {
        // given
        final VendorList vendorList = givenVendorList(
                givenVendor(1, "2024-01-01T00:00:00Z"),
                givenVendor(2, null),
                givenVendor(3, "2025-01-01T00:00:00Z"),
                givenVendor(4, "2024-06-01T12:00:00Z"));

        // when
        final var deletedIds = target.extractDeletedVendorIds(vendorList);

        // then
        assertThat(deletedIds).containsExactly(1);
    }

    @Test
    public void refreshShouldUpdateDeletedVendorIdsAndIncrementOkMetric() throws JsonProcessingException {
        // given
        final Vendor vendor = givenVendor(1, "2024-01-01T00:00:00Z");
        final String responseBody = mapper.writeValueAsString(givenVendorList(vendor));
        givenHttpClientReturnsResponse(200, responseBody);

        // when
        target.refresh();

        // then
        assertThat(target.isDeleted(1)).isTrue();
        verify(metrics).updatePrivacyTcfVendorListLatestOkMetric();
        verify(metrics, never()).updatePrivacyTcfVendorListLatestErrorMetric();
    }

    @Test
    public void refreshShouldReplaceDeletedVendorIdsOnSubsequentSuccessfulFetch() throws JsonProcessingException {
        // given
        final Vendor vendor = givenVendor(1, "2024-01-01T00:00:00Z");
        final String responseBody = mapper.writeValueAsString(givenVendorList(vendor));
        final Vendor vendor2 = givenVendor(2, "2024-02-01T00:00:00Z");
        final String responseBody2 = mapper.writeValueAsString(givenVendorList(vendor2));
        given(httpClient.get(anyString(), anyLong()))
                .willReturn(
                        Future.succeededFuture(HttpClientResponse.of(200, null, responseBody)),
                        Future.succeededFuture(HttpClientResponse.of(200, null, responseBody2)));

        // when
        target.refresh();
        target.refresh();

        // then
        assertThat(target.isDeleted(1)).isFalse();
        assertThat(target.isDeleted(2)).isTrue();
    }

    @Test
    public void refreshShouldIncrementErrorMetricOnHttpFailure() {
        // given
        given(httpClient.get(anyString(), anyLong()))
                .willReturn(Future.failedFuture(new RuntimeException("connection failed")));

        // when
        target.refresh();

        // then
        assertThat(target.isDeleted(1)).isFalse();
        verify(metrics).updatePrivacyTcfVendorListLatestErrorMetric();
        verify(metrics, never()).updatePrivacyTcfVendorListLatestOkMetric();
    }

    @Test
    public void refreshShouldIncrementErrorMetricOnNonOkStatus() {
        // given
        givenHttpClientReturnsResponse(503, "{}");

        // when
        target.refresh();

        // then
        assertThat(target.isDeleted(1)).isFalse();
        verify(metrics).updatePrivacyTcfVendorListLatestErrorMetric();
    }

    @Test
    public void refreshShouldIncrementErrorMetricOnInvalidJson() {
        // given
        givenHttpClientReturnsResponse(200, "invalid-json");

        // when
        target.refresh();

        // then
        assertThat(target.isDeleted(1)).isFalse();
        verify(metrics).updatePrivacyTcfVendorListLatestErrorMetric();
    }

    @Test
    public void refreshShouldIncrementErrorMetricOnInvalidVendorList() throws JsonProcessingException {
        // given
        final Vendor vendor = givenVendor(42, "2024-01-01T00:00:00Z")
                .toBuilder().features(null).build();
        final String responseBody = mapper.writeValueAsString(givenVendorList(vendor));
        givenHttpClientReturnsResponse(200, responseBody);

        // when
        target.refresh();

        // then
        assertThat(target.isDeleted(1)).isFalse();
        verify(metrics).updatePrivacyTcfVendorListLatestErrorMetric();
    }

    @Test
    public void initializeShouldLoadDeletedVendorsFromCachedVendorList() {
        // given
        final VendorList vendorList = givenVendorList(givenVendor(42, "2024-01-01T00:00:00Z"));
        given(vendorListFileStore.getLatestVendorListFromCache(eq(CACHE_DIR))).willReturn(Optional.of(vendorList));

        // when
        target.initialize(Promise.promise());

        // then
        assertThat(target.isDeleted(42)).isTrue();
        assertThat(target.isDeleted(99)).isFalse();
    }

    @Test
    public void initializeShouldSchedulePeriodicRefresh() {
        // given
        given(vendorListFileStore.getLatestVendorListFromCache(eq(CACHE_DIR))).willReturn(Optional.empty());

        // when
        target.initialize(Promise.promise());

        // then
        verify(vertx).setPeriodic(eq(0L), eq(REFRESH_PERIOD_MS), any());
    }

    @Test
    public void initializeShouldCompleteInitializePromise() {
        // given
        given(vendorListFileStore.getLatestVendorListFromCache(eq(CACHE_DIR))).willReturn(Optional.empty());
        final Promise<Void> promise = Promise.promise();

        // when
        target.initialize(promise);

        // then
        assertThat(promise.future().succeeded()).isTrue();
    }

    @Test
    public void refreshShouldKeepLastGoodSetOnFailureAfterSuccessfulFetch() throws JsonProcessingException {
        // given
        final Vendor vendor = givenVendor(1, "2024-01-01T00:00:00Z");
        final String responseBody = mapper.writeValueAsString(givenVendorList(vendor));
        given(httpClient.get(anyString(), anyLong()))
                .willReturn(
                        Future.succeededFuture(HttpClientResponse.of(200, null, responseBody)),
                        Future.failedFuture(new RuntimeException("connection failed")));

        // when
        target.refresh();
        target.refresh();

        // then
        assertThat(target.isDeleted(1)).isTrue();
        verify(metrics).updatePrivacyTcfVendorListLatestOkMetric();
        verify(metrics).updatePrivacyTcfVendorListLatestErrorMetric();
    }

    private void givenHttpClientReturnsResponse(int statusCode, String response) {
        given(httpClient.get(anyString(), anyLong()))
                .willReturn(Future.succeededFuture(HttpClientResponse.of(statusCode, null, response)));
    }

    private static Vendor givenVendor(int id, String deletedDate) {
        return Vendor.builder()
                .id(id)
                .deletedDate(deletedDate != null ? Instant.parse(deletedDate) : null)
                .purposes(EnumSet.of(ONE))
                .legIntPurposes(EnumSet.of(TWO))
                .flexiblePurposes(EnumSet.noneOf(PurposeCode.class))
                .specialPurposes(EnumSet.noneOf(SpecialPurpose.class))
                .features(EnumSet.noneOf(Feature.class))
                .specialFeatures(EnumSet.noneOf(SpecialFeature.class))
                .build();
    }

    private static VendorList givenVendorList(Vendor... vendors) {
        return VendorList.of(
                1,
                Date.from(Instant.parse("2020-08-20T16:05:24Z")),
                Arrays.stream(vendors).collect(Collectors.toMap(Vendor::getId, Function.identity())));
    }
}
