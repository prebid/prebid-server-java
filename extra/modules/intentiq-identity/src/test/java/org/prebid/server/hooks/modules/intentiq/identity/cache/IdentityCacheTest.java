package org.prebid.server.hooks.modules.intentiq.identity.cache;

import com.codahale.metrics.MetricRegistry;
import com.iab.openrtb.request.Eid;
import com.iab.openrtb.request.Uid;
import io.vertx.core.Future;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.prebid.server.hooks.modules.intentiq.identity.metric.IntentiqIdentityMetrics;
import org.prebid.server.json.JacksonMapper;
import org.prebid.server.json.ObjectMapperProvider;

import java.util.List;

import static java.util.Collections.singletonList;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
public class IdentityCacheTest {

    private static final JacksonMapper MAPPER = new JacksonMapper(ObjectMapperProvider.mapper());

    // defaultTtl 12h, firstParty ceiling 24h, thirdParty ceiling 12h, device ceiling 1h, negative 2min,
    // in-progress 60s
    private static final CacheTtlPolicy POLICY =
            new CacheTtlPolicy(43_200_000L, 86_400_000L, 43_200_000L, 3_600_000L, 120_000L, 60_000L);

    private static final CacheKey IIQ = new CacheKey("iiq:uid-1", KeyType.THIRD_PARTY);
    private static final CacheKey PUBCID = new CacheKey("pubcid:p1", KeyType.FIRST_PARTY);
    private static final CacheKey DEV = new CacheKey("dev:ifa_ua_ip", KeyType.DEVICE);

    @Mock
    private IdentityStore store;

    private IdentityCache target;
    private MetricRegistry metricRegistry;

    private final List<Eid> eids = singletonList(Eid.builder()
            .source("intentiq.com")
            .uids(singletonList(Uid.builder().id("uid-1").build()))
            .build());

    @BeforeEach
    public void setUp() {
        metricRegistry = new MetricRegistry();
        target = new IdentityCache(100L, POLICY, store, MAPPER, new IntentiqIdentityMetrics(metricRegistry));
    }

    @Test
    public void getShouldReturnFromLocalCacheWithoutQueryingStoreAfterPut() {
        // given
        when(store.put(any(), any(), anyLong())).thenReturn(Future.succeededFuture());
        target.put(List.of(IIQ), eids, 1000L);

        // when
        final CacheResult result = target.get(List.of(IIQ)).result();

        // then
        assertThat(result.state()).isEqualTo(CacheResult.State.HIT);
        assertThat(result.eids()).isEqualTo(eids);
        verify(store, never()).get(any());
    }

    @Test
    public void putShouldRefreshExpiryWhenSameKeyWrittenAgain() {
        // given
        when(store.put(any(), any(), anyLong())).thenReturn(Future.succeededFuture());
        target.put(List.of(IIQ), eids, 1000L);

        // when — a second write to the same key updates the existing L1 entry (exercises expireAfterUpdate)
        target.put(List.of(IIQ), eids, 5000L);

        // then
        final CacheResult result = target.get(List.of(IIQ)).result();
        assertThat(result.state()).isEqualTo(CacheResult.State.HIT);
        assertThat(result.eids()).isEqualTo(eids);
    }

    @Test
    public void putShouldWriteToStoreWithEffectiveTtlFromCttl() {
        // given
        when(store.put(any(), any(), anyLong())).thenReturn(Future.succeededFuture());

        // when — cttl below every ceiling is used verbatim
        target.put(List.of(IIQ), eids, 1000L);

        // then
        verify(store).put(eq("iiq:uid-1"), any(), eq(1000L));
    }

    @Test
    public void putShouldUseDefaultTtlWhenCttlIsNotPositive() {
        // given
        when(store.put(any(), any(), anyLong())).thenReturn(Future.succeededFuture());

        // when — third-party ceiling (12h) equals default, so default applies
        target.put(List.of(IIQ), eids, 0L);

        // then
        verify(store).put(eq("iiq:uid-1"), any(), eq(43_200_000L));
    }

    @Test
    public void putShouldCapCttlAtPerTypeCeiling() {
        // given
        when(store.put(any(), any(), anyLong())).thenReturn(Future.succeededFuture());

        // when — huge cttl on a device key is capped at the 1h device ceiling
        target.put(List.of(DEV), eids, 999_999_999L);

        // then
        verify(store).put(eq("dev:ifa_ua_ip"), any(), eq(3_600_000L));
    }

    @Test
    public void putShouldWriteEntryUnderEveryKey() {
        // given
        when(store.put(any(), any(), anyLong())).thenReturn(Future.succeededFuture());

        // when
        target.put(List.of(IIQ, PUBCID), eids, 1000L);

        // then
        verify(store).put(eq("iiq:uid-1"), any(), eq(1000L));
        verify(store).put(eq("pubcid:p1"), any(), eq(1000L));
    }

    @Test
    public void getShouldReturnMissOnFullMiss() {
        // given
        when(store.get(any())).thenReturn(Future.succeededFuture(null));

        // when
        final CacheResult result = target.get(List.of(IIQ)).result();

        // then
        assertThat(result.state()).isEqualTo(CacheResult.State.MISS);
        verify(store).get("iiq:uid-1");
    }

    @Test
    public void getShouldReturnMissForEmptyKeys() {
        // when
        final CacheResult result = target.get(List.of()).result();

        // then
        assertThat(result.state()).isEqualTo(CacheResult.State.MISS);
        verify(store, never()).get(any());
    }

    @Test
    public void getShouldFallOpenToMissWhenStoreFails() {
        // given
        when(store.get(any())).thenReturn(Future.failedFuture(new RuntimeException("down")));

        // when
        final Future<CacheResult> future = target.get(List.of(IIQ));

        // then
        assertThat(future.succeeded()).isTrue();
        assertThat(future.result().state()).isEqualTo(CacheResult.State.MISS);
    }

    @Test
    public void getShouldReturnHitFromStoreWhenPresentAndNotExpired() {
        // given
        when(store.get("iiq:uid-1"))
                .thenReturn(Future.succeededFuture(positiveEntry(System.currentTimeMillis() + 60_000L)));

        // when
        final CacheResult result = target.get(List.of(IIQ)).result();

        // then
        assertThat(result.state()).isEqualTo(CacheResult.State.HIT);
        assertThat(result.eids()).isEqualTo(eids);
    }

    @Test
    public void getShouldReturnMissWhenStoreEntryIsExpired() {
        // given
        when(store.get(any())).thenReturn(Future.succeededFuture(positiveEntry(System.currentTimeMillis() - 1L)));

        // when
        final CacheResult result = target.get(List.of(IIQ)).result();

        // then
        assertThat(result.state()).isEqualTo(CacheResult.State.MISS);
    }

    @Test
    public void getShouldPromoteStoreHitToLocalCacheAndNotQueryStoreAgain() {
        // given
        when(store.get("iiq:uid-1"))
                .thenReturn(Future.succeededFuture(positiveEntry(System.currentTimeMillis() + 60_000L)));
        target.get(List.of(IIQ)).result();

        // when — second lookup
        final CacheResult result = target.get(List.of(IIQ)).result();

        // then
        assertThat(result.eids()).isEqualTo(eids);
        verify(store).get("iiq:uid-1");
    }

    @Test
    public void getShouldReturnHighestPriorityHitAndBackfillMissedKeys() {
        // given — first (highest-priority) key misses, second hits
        lenient().when(store.put(any(), any(), anyLong())).thenReturn(Future.succeededFuture());
        when(store.get("iiq:uid-1")).thenReturn(Future.succeededFuture(null));
        when(store.get("pubcid:p1"))
                .thenReturn(Future.succeededFuture(positiveEntry(System.currentTimeMillis() + 60_000L)));

        // when
        final CacheResult result = target.get(List.of(IIQ, PUBCID)).result();

        // then — the hit is returned and the missed higher-priority key is back-filled
        assertThat(result.state()).isEqualTo(CacheResult.State.HIT);
        assertThat(result.eids()).isEqualTo(eids);
        verify(store).put(eq("iiq:uid-1"), any(), anyLong());
    }

    @Test
    public void getShouldBackfillFromLocalHitUnderOtherKeys() {
        // given — IIQ already in local cache, PUBCID not present anywhere
        when(store.put(any(), any(), anyLong())).thenReturn(Future.succeededFuture());
        target.put(List.of(IIQ), eids, 60_000L);

        // when — lookup with both keys hits IIQ in L1 and back-fills PUBCID
        final CacheResult result = target.get(List.of(IIQ, PUBCID)).result();

        // then
        assertThat(result.state()).isEqualTo(CacheResult.State.HIT);
        verify(store).put(eq("pubcid:p1"), any(), anyLong());
    }

    @Test
    public void putNegativeShouldYieldNegativeResultWithoutQueryingStore() {
        // given
        when(store.put(any(), any(), anyLong())).thenReturn(Future.succeededFuture());
        target.putNegative(List.of(IIQ), 0L);

        // when
        final CacheResult result = target.get(List.of(IIQ)).result();

        // then
        assertThat(result.state()).isEqualTo(CacheResult.State.NEGATIVE);
        verify(store, never()).get(any());
    }

    @Test
    public void putNegativeShouldWriteSentinelUnderAllKeysWithDefaultTtlWhenCttlAbsent() {
        // given
        when(store.put(any(), any(), anyLong())).thenReturn(Future.succeededFuture());

        // when
        target.putNegative(List.of(IIQ, PUBCID), 0L);

        // then
        verify(store).put(eq("iiq:uid-1"), any(), eq(120_000L));
        verify(store).put(eq("pubcid:p1"), any(), eq(120_000L));
    }

    @Test
    public void putNegativeShouldHonorResponseCttlAsSuppressionTtl() {
        // given — the BE signals the suppression window via cttl on an empty/invalid response
        when(store.put(any(), any(), anyLong())).thenReturn(Future.succeededFuture());

        // when
        target.putNegative(List.of(IIQ), 600_000L);

        // then
        verify(store).put(eq("iiq:uid-1"), any(), eq(600_000L));
    }

    @Test
    public void putNegativeShouldCapResponseCttlAtFirstPartyCeiling() {
        // given
        when(store.put(any(), any(), anyLong())).thenReturn(Future.succeededFuture());

        // when — an absurd cttl is bounded by the first-party ceiling (24h)
        target.putNegative(List.of(IIQ), 999_999_999_999L);

        // then
        verify(store).put(eq("iiq:uid-1"), any(), eq(86_400_000L));
    }

    @Test
    public void getShouldReturnNegativeFromStoreSentinel() {
        // given
        when(store.get("iiq:uid-1"))
                .thenReturn(Future.succeededFuture(negativeEntry(System.currentTimeMillis() + 60_000L)));

        // when
        final CacheResult result = target.get(List.of(IIQ)).result();

        // then
        assertThat(result.state()).isEqualTo(CacheResult.State.NEGATIVE);
    }

    @Test
    public void putShouldStoreLocallyEvenWhenStorePutFails() {
        // given
        when(store.put(any(), any(), anyLong())).thenReturn(Future.failedFuture(new RuntimeException("down")));

        // when
        target.put(List.of(IIQ), eids, 1000L);

        // then — local layer still serves the value without touching the store
        assertThat(target.get(List.of(IIQ)).result().eids()).isEqualTo(eids);
        verify(store, never()).get(any());
    }

    @Test
    public void putInProgressShouldWriteSentinelUnderAllKeysWithInProgressTtl() {
        // given
        when(store.put(any(), any(), anyLong())).thenReturn(Future.succeededFuture());

        // when
        target.putInProgress(List.of(IIQ, PUBCID));

        // then — marker written under every key with the in-progress TTL (60s)
        verify(store).put(eq("iiq:uid-1"), any(), eq(60_000L));
        verify(store).put(eq("pubcid:p1"), any(), eq(60_000L));
    }

    @Test
    public void getShouldReturnInProgressAfterPutInProgress() {
        // given
        when(store.put(any(), any(), anyLong())).thenReturn(Future.succeededFuture());
        target.putInProgress(List.of(IIQ));

        // when
        final CacheResult result = target.get(List.of(IIQ)).result();

        // then — served from L1 without touching the store
        assertThat(result.state()).isEqualTo(CacheResult.State.IN_PROGRESS);
        assertThat(result.eids()).isEmpty();
        verify(store, never()).get(any());
    }

    @Test
    public void getShouldReturnInProgressFromStoreSentinel() {
        // given
        when(store.get("iiq:uid-1"))
                .thenReturn(Future.succeededFuture(inProgressEntry(System.currentTimeMillis() + 60_000L)));

        // when
        final CacheResult result = target.get(List.of(IIQ)).result();

        // then
        assertThat(result.state()).isEqualTo(CacheResult.State.IN_PROGRESS);
    }

    @Test
    public void getShouldPreferResolvedHitOverInProgressMarker() {
        // given — highest-priority key is in progress, a lower-priority key already resolved
        lenient().when(store.put(any(), any(), anyLong())).thenReturn(Future.succeededFuture());
        when(store.get("iiq:uid-1"))
                .thenReturn(Future.succeededFuture(inProgressEntry(System.currentTimeMillis() + 60_000L)));
        when(store.get("pubcid:p1"))
                .thenReturn(Future.succeededFuture(positiveEntry(System.currentTimeMillis() + 60_000L)));

        // when
        final CacheResult result = target.get(List.of(IIQ, PUBCID)).result();

        // then — the resolved entry wins over the in-progress marker
        assertThat(result.state()).isEqualTo(CacheResult.State.HIT);
        assertThat(result.eids()).isEqualTo(eids);
    }

    @Test
    public void getShouldRecordStoreGetErrorAndLatencyWhenL2GetFails() {
        // given
        when(store.get(any())).thenReturn(Future.failedFuture(new RuntimeException("down")));

        // when
        target.get(List.of(IIQ)).result();

        // then — the otherwise-swallowed L2 failure is counted, and latency is recorded regardless
        assertThat(counter("l2.get.error")).isEqualTo(1);
        assertThat(timerCount("l2.get.latency")).isEqualTo(1);
    }

    @Test
    public void putShouldRecordStorePutErrorAndLatencyWhenL2PutFails() {
        // given
        when(store.put(any(), any(), anyLong())).thenReturn(Future.failedFuture(new RuntimeException("down")));

        // when
        target.put(List.of(IIQ), eids, 1000L);

        // then
        assertThat(counter("l2.put.error")).isEqualTo(1);
        assertThat(timerCount("l2.put.latency")).isEqualTo(1);
    }

    @Test
    public void shouldExposeL1SizeGaugeReflectingCachedEntries() {
        // given
        when(store.put(any(), any(), anyLong())).thenReturn(Future.succeededFuture());

        // when
        target.put(List.of(IIQ), eids, 60_000L);

        // then — the L1 size gauge is registered and reflects the written entry
        assertThat(gauge("l1.size")).isEqualTo(1L);
        assertThat(metricRegistry.getGauges()).containsKey(metricName("l1.eviction"));
    }

    private long counter(String name) {
        return metricRegistry.counter(metricName(name)).getCount();
    }

    private long timerCount(String name) {
        return metricRegistry.timer(metricName(name)).getCount();
    }

    private Object gauge(String name) {
        return metricRegistry.getGauges().get(metricName(name)).getValue();
    }

    private static String metricName(String name) {
        return "modules.module.intentiq-identity.custom." + name;
    }

    private String positiveEntry(long exp) {
        return MAPPER.encodeToString(IdentityCache.CacheEntry.of(eids, false, false, exp));
    }

    private String negativeEntry(long exp) {
        return MAPPER.encodeToString(IdentityCache.CacheEntry.of(List.of(), true, false, exp));
    }

    private String inProgressEntry(long exp) {
        return MAPPER.encodeToString(IdentityCache.CacheEntry.of(List.of(), false, true, exp));
    }
}
