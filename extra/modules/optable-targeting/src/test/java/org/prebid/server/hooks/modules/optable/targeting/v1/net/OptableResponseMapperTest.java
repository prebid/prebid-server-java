package org.prebid.server.hooks.modules.optable.targeting.v1.net;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.prebid.server.hooks.modules.optable.targeting.model.net.HttpResponse;
import org.prebid.server.hooks.modules.optable.targeting.model.net.OptableCall;
import org.prebid.server.hooks.modules.optable.targeting.model.openrtb.TargetingResult;
import org.prebid.server.hooks.modules.optable.targeting.model.openrtb.User;
import org.prebid.server.hooks.modules.optable.targeting.v1.BaseOptableTest;
import org.prebid.server.json.DecodeException;
import org.prebid.server.json.JacksonMapper;
import org.prebid.server.json.ObjectMapperProvider;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

public class OptableResponseMapperTest extends BaseOptableTest {

    private OptableResponseMapper target;

    private final JacksonMapper mapper = new JacksonMapper(ObjectMapperProvider.mapper());

    @BeforeEach
    public void setUp() {

        target = new OptableResponseMapper(mapper);
    }

    @Test
    public void shouldNotFailWhenSourceIsNull() {
        // given and when
        final TargetingResult result = target.parse((OptableCall) null);

        //then
        assertThat(result).isNull();
    }

    @Test
    public void shouldNotFailWhenResponseIsNull() {
        // given
        final OptableCall optableCall = OptableCall.succeededHttp(null, null);

        //when
        final TargetingResult result = target.parse(optableCall);

        //then
        assertThat(result).isNull();
    }

    @Test
    public void shouldNotFailWhenResponseBodyIsWrong() {
        // given
        final HttpResponse response = givenSuccessResponse("{\"field'\": \"value\"}");
        final OptableCall optableCall = OptableCall.succeededHttp(null, response);

        //when
        final TargetingResult result = target.parse(optableCall);

        //then
        assertThat(result).isNotNull();
        assertThat(result.getOrtb2()).isNull();
    }

    @Test
    public void shouldParseRightResponse() {
        // given
        final HttpResponse response = givenSuccessResponse(givenBodyFromFile("targeting_response.json"));
        final OptableCall optableCall = OptableCall.succeededHttp(null, response);

        //when
        final TargetingResult result = target.parse(optableCall);

        //then
        assertThat(result).isNotNull();
        final User user = result.getOrtb2().getUser();
        assertThat(user.getEids().getFirst().getUids().getFirst().getId()).isEqualTo("uid_id1");
        assertThat(user.getData().getFirst().getSegment().getFirst().getId()).isEqualTo("segment_id");
    }

    @Test
    public void shouldFailWhenGotNotJsonString() {
        // given
        final HttpResponse response = givenSuccessResponse("random string");
        final OptableCall optableCall = OptableCall.succeededHttp(null, response);

        //when and then
        assertThrows(
                DecodeException.class,
                () -> target.parse(optableCall)
        );
    }

    private HttpResponse givenSuccessResponse(String body) {
        return HttpResponse.of(200, null, body);
    }
}
