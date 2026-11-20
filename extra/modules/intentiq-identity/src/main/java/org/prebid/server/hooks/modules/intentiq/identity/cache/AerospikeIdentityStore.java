package org.prebid.server.hooks.modules.intentiq.identity.cache;

import com.aerospike.client.AerospikeException;
import com.aerospike.client.Bin;
import com.aerospike.client.IAerospikeClient;
import com.aerospike.client.Key;
import com.aerospike.client.Record;
import com.aerospike.client.async.EventLoops;
import com.aerospike.client.listener.DeleteListener;
import com.aerospike.client.listener.RecordListener;
import com.aerospike.client.listener.WriteListener;
import com.aerospike.client.policy.Policy;
import com.aerospike.client.policy.WritePolicy;
import io.vertx.core.Context;
import io.vertx.core.Future;
import io.vertx.core.Promise;
import io.vertx.core.Vertx;

import java.util.Objects;

/**
 * Aerospike-backed {@link IdentityStore}: one record per key in {@code namespace.set}, the value in bin
 * {@code v}, with a per-record TTL. Uses the client's async API, so no thread waits on Aerospike.
 *
 * <p>The namespace must allow TTLs ({@code nsup-period} above 0, or {@code allow-ttl-without-nsup}), or
 * Aerospike rejects every write; the cache then fails open and works from the local layer only.
 */
public class AerospikeIdentityStore implements IdentityStore, AutoCloseable {

    private static final String VALUE_BIN = "v";

    private final IAerospikeClient client;
    private final EventLoops eventLoops;
    private final String namespace;
    private final String set;
    private final Policy readPolicy;
    private final WritePolicy deletePolicy;
    private final int timeoutMs;

    public AerospikeIdentityStore(IAerospikeClient client,
                                  EventLoops eventLoops,
                                  String namespace,
                                  String set,
                                  int timeoutMs) {
        this.client = Objects.requireNonNull(client);
        this.eventLoops = Objects.requireNonNull(eventLoops);
        this.namespace = Objects.requireNonNull(namespace);
        this.set = Objects.requireNonNull(set);
        this.timeoutMs = timeoutMs;
        this.readPolicy = new Policy();
        this.readPolicy.setTimeout(timeoutMs);
        this.deletePolicy = writePolicy(timeoutMs, 0);
    }

    @Override
    public Future<String> get(String key) {
        final Context context = Vertx.currentContext();
        final Promise<String> promise = Promise.promise();
        try {
            client.get(eventLoops.next(), new RecordListener() {
                @Override
                public void onSuccess(Key aerospikeKey, Record record) {
                    final Object value = record != null ? record.getValue(VALUE_BIN) : null;
                    complete(context, () -> promise.complete(value != null ? value.toString() : null));
                }

                @Override
                public void onFailure(AerospikeException exception) {
                    complete(context, () -> promise.fail(exception));
                }
            }, readPolicy, key(key), VALUE_BIN);
        } catch (AerospikeException e) {
            promise.fail(e);
        }
        return promise.future();
    }

    @Override
    public Future<Void> put(String key, String value, long ttlMs) {
        final Context context = Vertx.currentContext();
        final Promise<Void> promise = Promise.promise();
        try {
            client.put(eventLoops.next(), new WriteListener() {
                @Override
                public void onSuccess(Key aerospikeKey) {
                    complete(context, promise::complete);
                }

                @Override
                public void onFailure(AerospikeException exception) {
                    complete(context, () -> promise.fail(exception));
                }
            }, writePolicy(timeoutMs, expirationSeconds(ttlMs)), key(key), new Bin(VALUE_BIN, value));
        } catch (AerospikeException e) {
            promise.fail(e);
        }
        return promise.future();
    }

    @Override
    public Future<Void> delete(String key) {
        final Context context = Vertx.currentContext();
        final Promise<Void> promise = Promise.promise();
        try {
            client.delete(eventLoops.next(), new DeleteListener() {
                @Override
                public void onSuccess(Key aerospikeKey, boolean existed) {
                    complete(context, promise::complete);
                }

                @Override
                public void onFailure(AerospikeException exception) {
                    complete(context, () -> promise.fail(exception));
                }
            }, deletePolicy, key(key));
        } catch (AerospikeException e) {
            promise.fail(e);
        }
        return promise.future();
    }

    @Override
    public void close() {
        client.close();
        eventLoops.close();
    }

    private Key key(String key) {
        return new Key(namespace, set, key);
    }

    private static WritePolicy writePolicy(int timeoutMs, int expirationSeconds) {
        final WritePolicy policy = new WritePolicy();
        policy.setTimeout(timeoutMs);
        policy.expiration = expirationSeconds;
        return policy;
    }

    // Aerospike TTLs are whole seconds; round up so an entry never expires before the cache meant it to.
    static int expirationSeconds(long ttlMs) {
        final long seconds = (ttlMs + 999) / 1000;
        return (int) Math.max(1, Math.min(Integer.MAX_VALUE, seconds));
    }

    // Completes the caller's future on the caller's Vert.x context, not on the Aerospike event loop thread.
    private static void complete(Context context, Runnable completion) {
        if (context != null) {
            context.runOnContext(ignored -> completion.run());
        } else {
            completion.run();
        }
    }
}
