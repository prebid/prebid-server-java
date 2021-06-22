package org.prebid.server.model;

import lombok.AccessLevel;
import lombok.AllArgsConstructor;

import java.util.List;
import java.util.Map;
import java.util.Set;

@AllArgsConstructor(staticName = "of", access = AccessLevel.PRIVATE)
public class CaseInsensitiveMultiMap implements MultiMap {

    private final io.vertx.core.MultiMap delegate;

    public static CaseInsensitiveMultiMap of() {
        return of(io.vertx.core.MultiMap.caseInsensitiveMultiMap());
    }

    @Override
    public String get(CharSequence name) {
        return this.delegate.get(name);
    }

    @Override
    public String get(String name) {
        return this.delegate.get(name);
    }

    @Override
    public List<String> getAll(String name) {
        return this.delegate.getAll(name);
    }

    @Override
    public List<String> getAll(CharSequence name) {
        return this.delegate.getAll(name);
    }

    @Override
    public List<Map.Entry<String, String>> entries() {
        return this.delegate.entries();
    }

    @Override
    public boolean contains(String name) {
        return this.delegate.contains(name);
    }

    @Override
    public boolean contains(CharSequence name) {
        return this.delegate.contains(name);
    }

    @Override
    public boolean isEmpty() {
        return this.delegate.isEmpty();
    }

    @Override
    public Set<String> names() {
        return this.delegate.names();
    }

    public CaseInsensitiveMultiMap add(String name, String value) {
        delegate.add(name, value);
        return this;
    }

    public CaseInsensitiveMultiMap add(CharSequence name, CharSequence value) {
        delegate.add(name, value);
        return this;
    }

    public CaseInsensitiveMultiMap add(String name, Iterable<String> values) {
        delegate.add(name, values);
        return this;
    }

    public CaseInsensitiveMultiMap add(CharSequence name, Iterable<CharSequence> values) {
        delegate.add(name, values);
        return this;
    }

    public CaseInsensitiveMultiMap addAll(MultiMap map) {
        map.entries().forEach(entry -> delegate.add(entry.getKey(), entry.getValue()));
        return this;
    }

    public CaseInsensitiveMultiMap addAll(Map<String, String> map) {
        delegate.addAll(map);
        return this;
    }

    public CaseInsensitiveMultiMap set(String name, String value) {
        delegate.set(name, value);
        return this;
    }

    public CaseInsensitiveMultiMap set(CharSequence name, CharSequence value) {
        delegate.set(name, value);
        return this;
    }

    public CaseInsensitiveMultiMap set(String name, Iterable<String> values) {
        delegate.set(name, values);
        return this;
    }

    public CaseInsensitiveMultiMap set(CharSequence name, Iterable<CharSequence> values) {
        delegate.set(name, values);
        return this;
    }

    public CaseInsensitiveMultiMap setAll(MultiMap map) {
        map.entries().forEach(entry -> delegate.set(entry.getKey(), entry.getValue()));
        return this;
    }

    public CaseInsensitiveMultiMap setAll(Map<String, String> map) {
        delegate.setAll(map);
        return this;
    }

    public CaseInsensitiveMultiMap remove(String name) {
        delegate.remove(name);
        return this;
    }

    public CaseInsensitiveMultiMap remove(CharSequence name) {
        delegate.remove(name);
        return this;
    }

    public CaseInsensitiveMultiMap clear() {
        delegate.clear();
        return this;
    }
}
