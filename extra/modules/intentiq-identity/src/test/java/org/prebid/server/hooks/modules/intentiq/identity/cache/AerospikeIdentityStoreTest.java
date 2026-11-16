package org.prebid.server.hooks.modules.intentiq.identity.cache;

import com.aerospike.client.AerospikeException;
import com.aerospike.client.Bin;
import com.aerospike.client.IAerospikeClient;
import com.aerospike.client.Key;
import com.aerospike.client.Record;
import com.aerospike.client.ResultCode;
import com.aerospike.client.async.EventLoops;
import com.aerospike.client.listener.DeleteListener;
import com.aerospike.client.listener.RecordListener;
import com.aerospike.client.listener.WriteListener;
import com.aerospike.client.policy.Policy;
import com.aerospike.client.policy.WritePolicy;
import io.vertx.core.Future;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
public class AerospikeIdentityStoreTest {

    @Mock
    private IAerospikeClient client;

    @Mock
    private EventLoops eventLoops;

    private AerospikeIdentityStore target;

    @BeforeEach
    public void setUp() {
        target = new AerospikeIdentityStore(client, eventLoops, "ns", "iiq", 100);
    }

    @Test
    public void getShouldReturnStoredValue() {
        // given
        doAnswer(invocation -> {
            invocation.<RecordListener>getArgument(1).onSuccess(invocation.getArgument(3),
                    new Record(Map.of("v", "cached"), 1, 100));
            return null;
        }).when(client).get(any(), any(RecordListener.class), any(Policy.class), any(Key.class), eq("v"));

        // when
        final Future<String> result = target.get("key");

        // then
        assertThat(result.result()).isEqualTo("cached");
        final ArgumentCaptor<Key> key = ArgumentCaptor.forClass(Key.class);
        verify(client).get(any(), any(RecordListener.class), any(Policy.class), key.capture(), eq("v"));
        assertThat(key.getValue().namespace).isEqualTo("ns");
        assertThat(key.getValue().setName).isEqualTo("iiq");
        assertThat(key.getValue().userKey.toString()).isEqualTo("key");
    }

    @Test
    public void getShouldReturnNullWhenRecordIsMissing() {
        // given
        doAnswer(invocation -> {
            invocation.<RecordListener>getArgument(1).onSuccess(invocation.getArgument(3), null);
            return null;
        }).when(client).get(any(), any(RecordListener.class), any(Policy.class), any(Key.class), eq("v"));

        // when and then
        final Future<String> result = target.get("key");
        assertThat(result.succeeded()).isTrue();
        assertThat(result.result()).isNull();
    }

    @Test
    public void getShouldFailWhenAerospikeFails() {
        // given
        doAnswer(invocation -> {
            invocation.<RecordListener>getArgument(1).onFailure(new AerospikeException(ResultCode.TIMEOUT));
            return null;
        }).when(client).get(any(), any(RecordListener.class), any(Policy.class), any(Key.class), eq("v"));

        // when and then
        assertThat(target.get("key").failed()).isTrue();
    }

    @Test
    public void putShouldWriteValueWithTtlRoundedUpToSeconds() {
        // given
        doAnswer(invocation -> {
            invocation.<WriteListener>getArgument(1).onSuccess(invocation.getArgument(3));
            return null;
        }).when(client).put(any(), any(WriteListener.class), any(WritePolicy.class), any(Key.class), any(Bin.class));

        // when
        final Future<Void> result = target.put("key", "value", 1_500L);

        // then
        assertThat(result.succeeded()).isTrue();
        final ArgumentCaptor<WritePolicy> policy = ArgumentCaptor.forClass(WritePolicy.class);
        final ArgumentCaptor<Bin> bin = ArgumentCaptor.forClass(Bin.class);
        verify(client).put(any(), any(WriteListener.class), policy.capture(), any(Key.class), bin.capture());
        assertThat(policy.getValue().expiration).isEqualTo(2);
        assertThat(bin.getValue().name).isEqualTo("v");
        assertThat(bin.getValue().value.toString()).isEqualTo("value");
    }

    @Test
    public void putShouldFailWhenAerospikeRejectsTheWrite() {
        // given
        doAnswer(invocation -> {
            invocation.<WriteListener>getArgument(1).onFailure(new AerospikeException(ResultCode.FAIL_FORBIDDEN));
            return null;
        }).when(client).put(any(), any(WriteListener.class), any(WritePolicy.class), any(Key.class), any(Bin.class));

        // when and then
        assertThat(target.put("key", "value", 1_000L).failed()).isTrue();
    }

    @Test
    public void deleteShouldSucceedWhetherOrNotTheRecordExisted() {
        // given
        doAnswer(invocation -> {
            invocation.<DeleteListener>getArgument(1).onSuccess(invocation.getArgument(3), false);
            return null;
        }).when(client).delete(any(), any(DeleteListener.class), any(WritePolicy.class), any(Key.class));

        // when and then
        assertThat(target.delete("key").succeeded()).isTrue();
    }

    @Test
    public void expirationSecondsShouldNeverBeBelowOneSecond() {
        assertThat(AerospikeIdentityStore.expirationSeconds(1L)).isEqualTo(1);
        assertThat(AerospikeIdentityStore.expirationSeconds(0L)).isEqualTo(1);
        assertThat(AerospikeIdentityStore.expirationSeconds(60_000L)).isEqualTo(60);
    }

    @Test
    public void closeShouldCloseClientAndEventLoops() {
        // when
        target.close();

        // then
        verify(client).close();
        verify(eventLoops).close();
    }
}
