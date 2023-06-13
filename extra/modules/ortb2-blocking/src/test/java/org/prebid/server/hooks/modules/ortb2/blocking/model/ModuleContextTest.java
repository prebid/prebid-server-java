package org.prebid.server.hooks.modules.ortb2.blocking.model;

import org.junit.Test;
import org.prebid.server.auction.versionconverter.OrtbVersion;
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
        final ModuleContext initialContext = emptyContext
                .with("bidder1", OrtbVersion.ORTB_2_6)
                .with("bidder1", bidder1BlockedAttributes);

        final BlockedAttributes bidder2BlockedAttributes = attributes(singletonList("domain2.com"));

        // when
        final ModuleContext context = initialContext
                .with("bidder2", OrtbVersion.ORTB_2_5)
                .with("bidder2", bidder2BlockedAttributes);

        // then
        assertThat(context.ortbVersionOf("bidder1")).isSameAs(OrtbVersion.ORTB_2_6);
        assertThat(context.blockedAttributesFor("bidder1")).isSameAs(bidder1BlockedAttributes);
        assertThat(context.ortbVersionOf("bidder2")).isSameAs(OrtbVersion.ORTB_2_5);
        assertThat(context.blockedAttributesFor("bidder2")).isSameAs(bidder2BlockedAttributes);

        // initial context shouldn't change
        assertThat(initialContext.ortbVersionOf("bidder1")).isSameAs(OrtbVersion.ORTB_2_6);
        assertThat(initialContext.blockedAttributesFor("bidder1")).isSameAs(bidder1BlockedAttributes);
        assertThat(initialContext.ortbVersionOf("bidder2")).isNull();
        assertThat(initialContext.blockedAttributesFor("bidder2")).isNull();
    }

    @Test
    public void shouldCreateContextWithReplacedMapping() {
        // given
        final BlockedAttributes initialBlockedAttributes = attributes(singletonList("domain1.com"));
        final ModuleContext initialContext = emptyContext
                .with("bidder1", OrtbVersion.ORTB_2_5)
                .with("bidder1", initialBlockedAttributes);

        final BlockedAttributes replacementBlockedAttributes = attributes(singletonList("domain2com"));

        // when
        final ModuleContext context = initialContext
                .with("bidder1", OrtbVersion.ORTB_2_6)
                .with("bidder1", replacementBlockedAttributes);

        // then
        assertThat(context.ortbVersionOf("bidder1")).isSameAs(OrtbVersion.ORTB_2_6);
        assertThat(context.blockedAttributesFor("bidder1")).isSameAs(replacementBlockedAttributes);

        // initial context shouldn't change
        assertThat(initialContext.ortbVersionOf("bidder1")).isSameAs(OrtbVersion.ORTB_2_5);
        assertThat(initialContext.blockedAttributesFor("bidder1")).isSameAs(initialBlockedAttributes);
    }

    private static BlockedAttributes attributes(List<String> badv) {
        return BlockedAttributes.builder().badv(badv).build();
    }
}
