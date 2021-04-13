package org.prebid.server.hooks;

import org.prebid.server.hooks.v1.Hook;
import org.prebid.server.hooks.v1.Module;
import org.prebid.server.hooks.v1.auction.AuctionResponseHook;
import org.prebid.server.hooks.v1.auction.RawAuctionRequestHook;
import org.prebid.server.hooks.v1.bidder.BidderRequestHook;
import org.prebid.server.hooks.v1.bidder.RawBidderResponseHook;
import org.prebid.server.hooks.v1.entrypoint.EntrypointHook;

import java.util.Collection;
import java.util.Objects;
import java.util.Set;

/**
 * Provides simple access to all {@link Hook}s registered in application.
 */
public class HookCatalog {

    private final Set<Module> modules;

    public HookCatalog(Set<Module> modules) {
        this.modules = Objects.requireNonNull(modules);
    }

    public EntrypointHook entrypointHookBy(String moduleCode, String hookImplCode) {
        return getHookBy(moduleCode, hookImplCode, EntrypointHook.class);
    }

    public RawAuctionRequestHook rawAuctionRequestHookBy(String moduleCode, String hookImplCode) {
        return getHookBy(moduleCode, hookImplCode, RawAuctionRequestHook.class);
    }

    public BidderRequestHook bidderRequestHookBy(String moduleCode, String hookImplCode) {
        return getHookBy(moduleCode, hookImplCode, BidderRequestHook.class);
    }

    public RawBidderResponseHook rawBidderResponseHookBy(String moduleCode, String hookImplCode) {
        return getHookBy(moduleCode, hookImplCode, RawBidderResponseHook.class);
    }

    public AuctionResponseHook auctionResponseHookBy(String moduleCode, String hookImplCode) {
        return getHookBy(moduleCode, hookImplCode, AuctionResponseHook.class);
    }

    private <T extends Hook> T getHookBy(String moduleCode, String hookImplCode, Class<T> clazz) {
        return modules.stream()
                .filter(module -> Objects.equals(module.code(), moduleCode))
                .map(Module::hooks)
                .flatMap(Collection::stream)
                .filter(hook -> Objects.equals(hook.code(), hookImplCode))
                .filter(clazz::isInstance)
                .map(clazz::cast)
                .findFirst()
                .orElse(null);
    }
}
