package org.prebid.server.privacy.gdpr.vendorlist;

import com.fasterxml.jackson.core.JsonProcessingException;
import io.vertx.core.Future;
import io.vertx.core.Vertx;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.prebid.server.VertxTest;
import org.prebid.server.metric.Metrics;
import org.prebid.server.privacy.gdpr.vendorlist.proto.Vendor;
import org.prebid.server.privacy.gdpr.vendorlist.proto.VendorList;
import org.prebid.server.vertx.httpclient.HttpClient;
import org.prebid.server.vertx.httpclient.model.HttpClientResponse;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;

import static java.util.Collections.emptyMap;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
public class LiveVendorListServiceTest extends VertxTest {

    private static final Instant NOW = Instant.parse("2024-06-01T12:00:00Z");
    private static final String LIVE_GVL_URL = "https://example.com";

    @Mock
    private Vertx vertx;
    @Mock
    private HttpClient httpClient;
    @Mock
    private Metrics metrics;

    private LiveVendorListService target;

    @BeforeEach
    public void setUp() {
        target = new LiveVendorListService(
                LIVE_GVL_URL,
                0,
                1000,
                vertx,
                httpClient,
                jacksonMapper,
                metrics,
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
        final String responseBody = givenLiveGvlJson(Map.of(42, "2024-01-01T00:00:00Z"));
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
        final VendorList vendorList = givenVendorList(Map.of(
                1, givenVendor(1, "2024-01-01T00:00:00Z"),
                2, givenVendor(2, null),
                3, givenVendor(3, "2025-01-01T00:00:00Z"),
                4, givenVendor(4, "2024-06-01T12:00:00Z")));

        // when
        final var deletedIds = target.extractDeletedVendorIds(vendorList);

        // then
        assertThat(deletedIds).containsExactly(1);
    }

    @Test
    public void refreshShouldUpdateDeletedVendorIdsAndIncrementOkMetric() throws JsonProcessingException {
        // given
        givenHttpClientReturnsResponse(200, givenLiveGvlJson(Map.of(1, "2024-01-01T00:00:00Z")));

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
        given(httpClient.get(anyString(), anyLong()))
                .willReturn(
                        Future.succeededFuture(HttpClientResponse.of(
                                200, null, givenLiveGvlJson(Map.of(1, "2024-01-01T00:00:00Z")))),
                        Future.succeededFuture(HttpClientResponse.of(
                                200, null, givenLiveGvlJson(Map.of(2, "2024-02-01T00:00:00Z")))));

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
    public void refreshShouldIncrementErrorMetricWhenNoDeletedVendorsInResponse() throws JsonProcessingException {
        // given
        givenHttpClientReturnsResponse(200, givenLiveGvlJson(emptyMap()));

        // when
        target.refresh();

        // then
        assertThat(target.isDeleted(1)).isFalse();
        verify(metrics).updatePrivacyTcfVendorListLatestErrorMetric();
        verify(metrics, never()).updatePrivacyTcfVendorListLatestOkMetric();
    }

    @Test
    public void refreshShouldKeepLastGoodSetOnFailureAfterSuccessfulFetch() throws JsonProcessingException {
        // given
        given(httpClient.get(anyString(), anyLong()))
                .willReturn(
                        Future.succeededFuture(HttpClientResponse.of(
                                200, null, givenLiveGvlJson(Map.of(1, "2024-01-01T00:00:00Z")))),
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

    private String givenLiveGvlJson(Map<Integer, String> vendorIdToDeletedDate) throws JsonProcessingException {
        final Map<Integer, Object> vendors = new HashMap<>();
        for (Map.Entry<Integer, String> entry : vendorIdToDeletedDate.entrySet()) {
            vendors.put(entry.getKey(), Map.of("id", entry.getKey(), "deletedDate", entry.getValue()));
        }
        return mapper.writeValueAsString(Map.of("vendors", vendors));
    }

    private static Vendor givenVendor(int id, String deletedDate) {
        return Vendor.builder()
                .id(id)
                .deletedDate(deletedDate != null ? Instant.parse(deletedDate) : null)
                .build();
    }

    private static VendorList givenVendorList(Map<Integer, Vendor> vendors) {
        return VendorList.of(1, Date.from(Instant.parse("2020-08-20T16:05:24Z")), vendors);
    }
}
