package org.prebid.server.spring.config.bidder;

import org.prebid.server.bidder.Adapter;
import org.prebid.server.bidder.Bidder;
import org.prebid.server.bidder.BidderDeps;
import org.prebid.server.bidder.DisabledAdapter;
import org.prebid.server.bidder.DisabledBidder;
import org.prebid.server.bidder.Usersyncer;
import org.prebid.server.proto.response.BidderInfo;
import org.prebid.server.spring.config.bidder.model.BidderConfigurationProperties;

import java.util.List;
import java.util.function.Supplier;

public class BidderDepsAssembler {

    private static final String ERROR_MESSAGE_TEMPLATE_FOR_DISABLED = "%s is not configured properly on this "
            + "Prebid Server deploy. If you believe this should work, contact the company hosting the service "
            + "and tell them to check their configuration.";

    private String bidderName;
    private boolean enabled;
    private List<String> deprecatedNames;
    private List<String> aliases;
    private BidderInfo bidderInfo;
    private Supplier<Usersyncer> usersyncerCreator;
    private Supplier<Bidder<?>> bidderCreator;
    private Supplier<Adapter<?, ?>> adapterCreator;

    private BidderDepsAssembler() {
        enabled = false;
    }

    public static BidderDepsAssembler forBidder(String bidderName) {
        final BidderDepsAssembler assembler = new BidderDepsAssembler();
        assembler.bidderName = bidderName;
        return assembler;
    }

    public BidderDepsAssembler enabled(boolean enabled) {
        this.enabled = enabled;
        return this;
    }

    public BidderDepsAssembler deprecatedNames(List<String> deprecatedNames) {
        this.deprecatedNames = deprecatedNames;
        return this;
    }

    public BidderDepsAssembler aliases(List<String> aliases) {
        this.aliases = aliases;
        return this;
    }

    public BidderDepsAssembler bidderInfo(BidderInfo bidderInfo) {
        this.bidderInfo = bidderInfo;
        return this;
    }

    public BidderDepsAssembler usersyncerCreator(Supplier<Usersyncer> usersyncerCreator) {
        this.usersyncerCreator = usersyncerCreator;
        return this;
    }

    public BidderDepsAssembler bidderCreator(Supplier<Bidder<?>> bidderCreator) {
        this.bidderCreator = bidderCreator;
        return this;
    }

    public BidderDepsAssembler adapterCreator(Supplier<Adapter<?, ?>> adapterCreator) {
        this.adapterCreator = adapterCreator;
        return this;
    }

    public BidderDepsAssembler withConfig(BidderConfigurationProperties configProperties) {
        enabled = configProperties.getEnabled();
        deprecatedNames = configProperties.getDeprecatedNames();
        aliases = configProperties.getAliases();
        return this;
    }

    public BidderDeps assemble() {
        final Usersyncer usersyncer = enabled ? usersyncerCreator.get() : null;

        final Bidder<?> bidder = enabled ? bidderCreator.get()
                : new DisabledBidder(String.format(ERROR_MESSAGE_TEMPLATE_FOR_DISABLED, bidderName));

        final Adapter<?, ?> adapter = enabled ? (adapterCreator != null ? adapterCreator.get() : null)
                : new DisabledAdapter(String.format(ERROR_MESSAGE_TEMPLATE_FOR_DISABLED, bidderName));

        return BidderDeps.builder()
                .name(bidderName)
                .deprecatedNames(deprecatedNames)
                .aliases(aliases)
                .bidderInfo(bidderInfo)
                .usersyncer(usersyncer)
                .bidder(bidder)
                .adapter(adapter)
                .build();
    }
}
