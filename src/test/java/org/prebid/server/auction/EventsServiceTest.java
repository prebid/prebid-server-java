package org.prebid.server.auction;

import org.junit.Test;
import org.prebid.server.proto.openrtb.ext.response.Events;

import java.util.Collections;

import static org.assertj.core.api.Assertions.assertThat;

public class EventsServiceTest {

    @Test
    public void shouldReturnNullWhenEnabledListIsNull() {
        // given
        final EventsService eventsService = new EventsService(null, "http://external.org");

        // when
        final Events events = eventsService.createEvents("publisherId", "bidId", "bidder");

        // then
        assertThat(events).isEqualTo(null);
    }

    @Test
    public void shouldReturnNullWhenPublisherIdIsNotInEnabledList() {
        // given
        final EventsService eventsService = new EventsService(Collections.emptyList(), "http://external.org");

        // when
        final Events events = eventsService.createEvents("publisherId", "bidId", "bidder");

        // then
        assertThat(events).isEqualTo(null);
    }

    @Test
    public void shouldReturnNewEventsObject() {
        // given
        final EventsService eventsService = new EventsService(Collections.singletonList("publisherId"),
                "http://external.org");

        // when
        final Events events = eventsService.createEvents("publisherId", "bidId", "bidder");

        // then
        assertThat(events).isEqualTo(Events.of("http://external.org/event?type=win&bidid=bidId&bidder=bidder",
                "http://external.org/event?type=view&bidid=bidId&bidder=bidder"));
    }
}
