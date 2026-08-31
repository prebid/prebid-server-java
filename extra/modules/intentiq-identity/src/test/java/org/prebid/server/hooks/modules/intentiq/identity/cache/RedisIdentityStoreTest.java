package org.prebid.server.hooks.modules.intentiq.identity.cache;

import io.vertx.core.Future;
import io.vertx.redis.client.RedisAPI;
import io.vertx.redis.client.Response;
import io.vertx.redis.client.impl.types.SimpleStringType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
public class RedisIdentityStoreTest {

    @Mock
    private RedisAPI redis;

    @Captor
    private ArgumentCaptor<List<String>> setArgsCaptor;

    private RedisIdentityStore target;

    @BeforeEach
    public void setUp() {
        target = new RedisIdentityStore(redis);
    }

    @Test
    public void putShouldIssueSetWithPxTtl() {
        // given
        when(redis.set(any())).thenReturn(Future.succeededFuture());

        // when
        target.put("key", "value", 1000L);

        // then
        verify(redis).set(setArgsCaptor.capture());
        assertThat(setArgsCaptor.getValue()).containsExactly("key", "value", "PX", "1000");
    }

    @Test
    public void getShouldReturnResponseAsString() {
        // given
        when(redis.get("key")).thenReturn(Future.succeededFuture(SimpleStringType.create("value")));

        // when and then
        assertThat(target.get("key").result()).isEqualTo("value");
    }

    @Test
    public void getShouldReturnNullWhenKeyAbsent() {
        // given
        when(redis.get("key")).thenReturn(Future.succeededFuture(null));

        // when and then
        assertThat(target.get("key").result()).isNull();
    }

    @Test
    public void dbSizeShouldReturnResponseAsLong() {
        // given
        final Response response = mock(Response.class);
        when(response.toLong()).thenReturn(42L);
        when(redis.dbsize()).thenReturn(Future.succeededFuture(response));

        // when and then
        assertThat(target.dbSize().result()).isEqualTo(42L);
    }

    @Test
    public void dbSizeShouldReturnZeroWhenResponseNull() {
        // given
        when(redis.dbsize()).thenReturn(Future.succeededFuture(null));

        // when and then
        assertThat(target.dbSize().result()).isZero();
    }

    @Test
    public void evictedKeysShouldParseValueFromInfoStats() {
        // given
        when(redis.info(List.of("stats"))).thenReturn(Future.succeededFuture(
                SimpleStringType.create("# Stats\r\nexpired_keys:5\r\nevicted_keys:17\r\nkeyspace_hits:1\r\n")));

        // when and then
        assertThat(target.evictedKeys().result()).isEqualTo(17L);
    }

    @Test
    public void evictedKeysShouldReturnZeroWhenResponseNull() {
        // given
        when(redis.info(List.of("stats"))).thenReturn(Future.succeededFuture(null));

        // when and then
        assertThat(target.evictedKeys().result()).isZero();
    }

    @Test
    public void evictedKeysShouldReturnZeroWhenFieldMissing() {
        // given
        when(redis.info(List.of("stats"))).thenReturn(Future.succeededFuture(
                SimpleStringType.create("# Stats\r\nexpired_keys:5\r\nkeyspace_hits:1\r\n")));

        // when and then
        assertThat(target.evictedKeys().result()).isZero();
    }

    @Test
    public void evictedKeysShouldReturnZeroWhenValueNotANumber() {
        // given
        when(redis.info(List.of("stats"))).thenReturn(Future.succeededFuture(
                SimpleStringType.create("evicted_keys:not-a-number\r\n")));

        // when and then
        assertThat(target.evictedKeys().result()).isZero();
    }
}
