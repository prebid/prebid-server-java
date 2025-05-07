package org.prebid.server.hooks.modules.liveintent.omni.channel.identity.model.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.prebid.server.hooks.modules.liveintent.omni.channel.identity.model.IdResResponse;
import org.prebid.server.json.JacksonMapper;

import static org.assertj.core.api.Assertions.assertThat;

public class IdResResponseTest {
    private JacksonMapper jacksonMapper;

    @BeforeEach
    public void setUp() {
        ObjectMapper mapper = new ObjectMapper();
        jacksonMapper = new JacksonMapper(mapper);
    }

    @Test
    public void shouldDecodeFromString() {
        IdResResponse result = jacksonMapper.decodeValue("{\"eids\": [ { \"source\": \"liveintent.com\", \"uids\": [ { \"atype\": 3, \"id\" : \"some_id\" } ] } ] }", IdResResponse.class);
        assertThat(result.getEids()).hasSize(1);
        assertThat(result.getEids().getFirst().getSource()).isEqualTo("liveintent.com");
        assertThat(result.getEids().getFirst().getUids()).hasSize(1);
        assertThat(result.getEids().getFirst().getUids().getFirst().getAtype()).isEqualTo(3);
        assertThat(result.getEids().getFirst().getUids().getFirst().getId()).isEqualTo("some_id");
    }
}
