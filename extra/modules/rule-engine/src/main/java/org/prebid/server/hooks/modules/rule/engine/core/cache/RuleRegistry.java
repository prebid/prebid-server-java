package org.prebid.server.hooks.modules.rule.engine.core.cache;

import com.github.benmanes.caffeine.cache.Caffeine;
import com.iab.openrtb.request.Imp;

import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.TimeUnit;

public class RulesCache {

    final ConcurrentMap<String, Rule<Imp, Imp, Imp>> cache = Caffeine.newBuilder()
            .expireAfterAccess(100, TimeUnit.HOURS)
            .maximumSize(10000)
            .<String, Rule<Imp, Imp, Imp>>build()
            .asMap();
}
