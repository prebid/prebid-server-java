package org.prebid.server.events;

import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.mockito.junit.MockitoJUnit;
import org.mockito.junit.MockitoRule;
import org.prebid.server.proto.openrtb.ext.response.Events;

import static org.assertj.core.api.Assertions.assertThat;

public class EventsServiceTest {

    @Rule
    public final MockitoRule mockitoRule = MockitoJUnit.rule();

    private EventsService eventsService;

    @Before
    public void setUp() {
        eventsService = new EventsService("http://external.org");
    }

    @Test
    public void createEventsShouldReturnNewEvent() {
        // when
        final Events events = eventsService.createEvent("bidId", "bidder");

        // then
        assertThat(events).isEqualTo(Events.of("http://external.org/event?type=win&bidid=bidId&bidder=bidder",
                "http://external.org/event?type=view&bidid=bidId&bidder=bidder"));
    }
}

