package org.prebid.server.hooks.modules.ortb2.blocking.model;

import org.junit.Test;
import org.prebid.server.hooks.modules.ortb2.blocking.core.model.BlockedAttributes;

import java.util.List;

import static java.util.Collections.singletonList;
import static org.assertj.core.api.Assertions.assertThat;

public class ModuleContextTest {

    private final ModuleContext emptyContext = ModuleContext.create();

    @Test
    public void shouldCreateContextWithAdditionalMapping() {
        // given
        final BlockedAttributes bidder1BlockedAttributes = attributes(singletonList("domain1.com"));
        final ModuleContext initialContext = emptyContext.with("bidder1", bidder1BlockedAttributes);

        final BlockedAttributes bidder2BlockedAttributes = attributes(singletonList("domain2.com"));

        // when
        final ModuleContext context = initialContext.with("bidder2", bidder2BlockedAttributes);

        // then
        assertThat(context.blockedAttributesFor("bidder1")).isSameAs(bidder1BlockedAttributes);
        assertThat(context.blockedAttributesFor("bidder2")).isSameAs(bidder2BlockedAttributes);

        // initial context shouldn't change
        assertThat(initialContext.blockedAttributesFor("bidder1")).isSameAs(bidder1BlockedAttributes);
        assertThat(initialContext.blockedAttributesFor("bidder2")).isNull();
    }

    @Test
    public void shouldCreateContextWithReplacedMapping() {
        // given
        final BlockedAttributes initialBlockedAttributes = attributes(singletonList("domain1.com"));
        final ModuleContext initialContext = emptyContext.with("bidder1", initialBlockedAttributes);

        final BlockedAttributes replacementBlockedAttributes = attributes(singletonList("domain2com"));

        // when
        final ModuleContext context = initialContext.with("bidder1", replacementBlockedAttributes);

        // then
        assertThat(context.blockedAttributesFor("bidder1")).isSameAs(replacementBlockedAttributes);

        // initial context shouldn't change
        assertThat(initialContext.blockedAttributesFor("bidder1")).isSameAs(initialBlockedAttributes);
    }

    private static BlockedAttributes attributes(List<String> badv) {
        return BlockedAttributes.builder().badv(badv).build();
    }
}
