package org.prebid.server.model;

import org.junit.Test;

import java.util.Arrays;

import static org.assertj.core.api.Assertions.assertThat;

public class CaseInsensitiveMultiMapTest {

    @Test
    public void shouldReturnMultipleValuesWhenAddedIndividuallyWithDifferentCase() {
        // when
        final CaseInsensitiveMultiMap map = CaseInsensitiveMultiMap.builder()
                .add("Key1", "value11")
                .add("kEy2", "value21")
                .add("keY1", "value12")
                .add("KEy2", "value22")
                .build();

        // then
        assertThat(map.getAll("key1")).containsOnly("value11", "value12");
        assertThat(map.getAll("key2")).containsOnly("value21", "value22");
    }

    @Test
    public void shouldReturnMultipleValuesWhenAddedInBulkAndIndividually() {
        // when
        final CaseInsensitiveMultiMap map = CaseInsensitiveMultiMap.builder()
                .add("key1", "value11")
                .add("key1", Arrays.<String>asList("value12", "value13"))
                .build();

        // then
        assertThat(map.getAll("key1")).containsOnly("value11", "value12", "value13");
    }

    @Test
    public void shouldReturnMultipleValuesWhenAddedUsingAddAll() {
        // given
        final CaseInsensitiveMultiMap anotherMap = CaseInsensitiveMultiMap.builder()
                .add("key1", "value13")
                .add("key1", "value14")
                .add("key3", "value31")
                .build();

        // when
        final CaseInsensitiveMultiMap map = CaseInsensitiveMultiMap.builder()
                .add("key1", "value11")
                .add("key1", "value12")
                .add("key2", "value21")
                .addAll(anotherMap)
                .build();

        // then
        assertThat(map.getAll("key1")).containsOnly("value11", "value12", "value13", "value14");
        assertThat(map.getAll("key2")).containsOnly("value21");
        assertThat(map.getAll("key3")).containsOnly("value31");
    }
}
