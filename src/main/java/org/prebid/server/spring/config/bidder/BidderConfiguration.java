package org.prebid.server.spring.config.bidder;

import org.prebid.server.bidder.Adapter;
import org.prebid.server.bidder.Bidder;
import org.prebid.server.bidder.BidderDeps;
import org.prebid.server.bidder.DisabledAdapter;
import org.prebid.server.bidder.DisabledBidder;
import org.prebid.server.bidder.MetaInfo;
import org.prebid.server.bidder.Usersyncer;

import java.util.List;

public abstract class BidderConfiguration {

    private static final String ERROR_MESSAGE_TEMPLATE_FOR_DISABLED = "%s is not configured properly on this "
            + "Prebid Server deploy. If you believe this should work, contact the company hosting the service "
            + "and tell them to check their configuration.";

    protected BidderDeps bidderDeps() {
        final String bidderName = bidderName();
        final MetaInfo metaInfo = createMetaInfo();
        final boolean enabled = metaInfo.info().isEnabled();

        final Usersyncer usersyncer = createUsersyncer();

        final Bidder<?> bidder = enabled ? createBidder(metaInfo)
                : new DisabledBidder(String.format(ERROR_MESSAGE_TEMPLATE_FOR_DISABLED, bidderName));

        final Adapter<?, ?> adapter = enabled ? createAdapter(usersyncer)
                : new DisabledAdapter(String.format(ERROR_MESSAGE_TEMPLATE_FOR_DISABLED, bidderName));

        return BidderDeps.builder()
                .name(bidderName)
                .deprecatedNames(deprecatedNames())
                .aliases(aliases())
                .metaInfo(metaInfo)
                .usersyncer(usersyncer)
                .bidder(bidder)
                .adapter(adapter)
                .build();
    }

    protected abstract String bidderName();

    protected abstract List<String> deprecatedNames();

    protected abstract List<String> aliases();

    protected abstract MetaInfo createMetaInfo();

    protected abstract Usersyncer createUsersyncer();

    protected abstract Bidder<?> createBidder(MetaInfo metaInfo);

    protected abstract Adapter<?, ?> createAdapter(Usersyncer usersyncer);
}
