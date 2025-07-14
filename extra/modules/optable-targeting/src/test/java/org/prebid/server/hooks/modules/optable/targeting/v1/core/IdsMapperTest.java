package org.prebid.server.hooks.modules.optable.targeting.v1.core;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.iab.openrtb.request.BidRequest;
import com.iab.openrtb.request.Device;
import com.iab.openrtb.request.Eid;
import com.iab.openrtb.request.Imp;
import com.iab.openrtb.request.Uid;
import com.iab.openrtb.request.User;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.prebid.server.hooks.modules.optable.targeting.model.Id;
import org.prebid.server.hooks.modules.optable.targeting.model.openrtb.ExtUserOptable;
import org.prebid.server.json.ObjectMapperProvider;
import org.prebid.server.proto.openrtb.ext.request.ExtUser;

import java.util.List;
import java.util.Map;
import java.util.function.UnaryOperator;

import static java.util.Collections.singletonList;
import static org.assertj.core.api.Assertions.assertThat;

public class IdsMapperTest {

    private final ObjectMapper objectMapper = ObjectMapperProvider.mapper();

    private IdsMapper target;

    private final Map<String, String> ppidMapping = Map.of("test.com", "c");

    @BeforeEach
    public void setUp() {
        target = new IdsMapper(objectMapper, 0.01);
    }

    @Test
    public void shouldMapBidRequestToAllPossibleIds() {
        //given
        final BidRequest bidRequest = givenBidRequestWithEids(Map.of("id5-sync.com", "id5_id",
                "test.com", "test_id", "utiq.com", "utiq_id"));

        // when
        final List<Id> ids = target.toIds(bidRequest, ppidMapping);

        // then
        assertThat(ids).isNotNull()
                .contains(Id.of(Id.EMAIL, "email"))
                .contains(Id.of(Id.PHONE, "123"))
                .contains(Id.of(Id.ZIP, "321"))
                .contains(Id.of(Id.OPTABLE_VID, "vid"))
                .contains(Id.of(Id.GOOGLE_GAID, "ifa"))
                .doesNotContain(Id.of(Id.APPLE_IDFA, "ifa"))
                .contains(Id.of(Id.ID5, "id5_id"))
                .contains(Id.of(Id.UTIQ, "utiq_id"))
                .contains(Id.of("c", "test_id"));
    }

    @Test
    public void shouldMapNothing() {
        //given
        final BidRequest bidRequest = givenBidRequest(bidRequestBuilder -> bidRequestBuilder);

        // when
        final List<Id> ids = target.toIds(bidRequest, ppidMapping);

        // then
        assertThat(ids).isNotNull();
    }

    private BidRequest givenBidRequestWithEids(Map<String, String> eids) {
        return givenBidRequest(builder -> {
            final JsonNode extUserOptable = objectMapper.convertValue(givenOptable(), JsonNode.class);

            builder.device(givenDevice())
                    .user(givenUser(userBuilder -> {
                        final ExtUser extUser = ExtUser.builder().build();
                        extUser.addProperty("optable", extUserOptable);
                        userBuilder.eids(toEids(eids))
                                .ext(extUser)
                                .build();

                        return userBuilder;
                    }));

            return builder;
        });
    }

    private static BidRequest givenBidRequest(UnaryOperator<BidRequest.BidRequestBuilder> bidRequestCustomizer) {
        return bidRequestCustomizer.apply(BidRequest.builder()
                        .id("requestId")
                        .imp(singletonList(Imp.builder()
                                .id("impId")
                                .build())))
                .build();
    }

    private ExtUserOptable givenOptable() {
        return ExtUserOptable.builder()
                .email("email")
                .phone("123")
                .zip("321")
                .vid("vid")
                .build();
    }

    private Device givenDevice() {
        return Device.builder()
                .ip("127.0.0.1")
                .ipv6("0:0:0:0:0:0:0:1")
                .lmt(0)
                .os("android")
                .ifa("ifa")
                .build();
    }

    private User givenUser(UnaryOperator<User.UserBuilder> userCustomizer) {
        return userCustomizer.apply(User.builder()).build();
    }

    private List<Eid> toEids(Map<String, String> eids) {
        return eids.entrySet()
                .stream()
                .map(it -> Eid.builder()
                        .source(it.getKey())
                        .uids(List.of(Uid.builder().id(it.getValue()).build()))
                        .build())
                .toList();
    }
}
