package org.prebid.server.hooks.modules.optable.targeting.v1.core;

import io.vertx.core.Future;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;
import org.prebid.server.cache.PbcStorageService;
import org.prebid.server.cache.proto.request.module.StorageDataType;
import org.prebid.server.cache.proto.response.module.ModuleCacheResponse;
import org.prebid.server.hooks.modules.optable.targeting.model.openrtb.Audience;
import org.prebid.server.hooks.modules.optable.targeting.model.openrtb.AudienceId;
import org.prebid.server.hooks.modules.optable.targeting.model.openrtb.Ortb2;
import org.prebid.server.hooks.modules.optable.targeting.model.openrtb.TargetingResult;
import org.prebid.server.hooks.modules.optable.targeting.model.openrtb.User;
import org.prebid.server.json.JacksonMapper;
import org.prebid.server.json.ObjectMapperProvider;

import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
public class CacheTest {

    @Mock
    private PbcStorageService pbcStorageService;

    @Spy
    private final JacksonMapper jacksonMapper = new JacksonMapper(ObjectMapperProvider.mapper());

    private final JacksonMapper mapper = new JacksonMapper(ObjectMapperProvider.mapper());

    private Cache target;

    @BeforeEach
    public void setUp() {
        target = new Cache(pbcStorageService, jacksonMapper);
    }

    @Test
    public void cacheShouldNotCallMapperIfNoEntry() {
        // given
        when(pbcStorageService.retrieveEntry(any(), any(), any()))
                .thenReturn(Future.succeededFuture(ModuleCacheResponse.empty()));

        // when
        final TargetingResult result = target.get("key").result();

        // then
        Assertions.assertThat(result).isNull();
        verify(jacksonMapper, times(0)).decodeValue(anyString(), eq(TargetingResult.class));
    }

    @Test
    public void cacheShouldReturnEntry() {
        // given
        final TargetingResult targetingResult = givenTargetingResult();
        when(pbcStorageService.retrieveEntry(any(), any(), any()))
                .thenReturn(Future.succeededFuture(ModuleCacheResponse.of(
                        "key",
                        StorageDataType.TEXT,
                        mapper.encodeToString(targetingResult))));

        // when
        final TargetingResult result = target.get("key").result();

        // then
        Assertions.assertThat(result)
                .isNotNull()
                .isEqualTo(targetingResult);

        verify(jacksonMapper, times(1)).decodeValue(anyString(), eq(TargetingResult.class));
    }

    @Test
    public void cacheShouldStoreEntry() {
        // given
        final TargetingResult targetingResult = givenTargetingResult();

        // when
        when(pbcStorageService.storeEntry(any(), any(), any(), any(), any(), any()))
                .thenReturn(Future.succeededFuture());
        final boolean result = target.put("key", targetingResult, 86400).succeeded();

        // then
        Assertions.assertThat(result).isTrue();
        verify(pbcStorageService, times(1)).storeEntry(
                eq("key"),
                eq(mapper.encodeToString(targetingResult)),
                eq(StorageDataType.TEXT),
                eq(86400),
                any(),
                any());
    }

    private TargetingResult givenTargetingResult() {
        return new TargetingResult(
                List.of(new Audience(
                        "provider",
                        List.of(new AudienceId("1")),
                        "keyspace",
                        0)),
                new Ortb2(new User(null, null)));
    }
}
