package org.prebid.server.bidder;

import com.fasterxml.jackson.databind.JsonNode;
import org.apache.commons.lang3.StringUtils;
import org.prebid.server.proto.request.CookieSyncRequest;
import org.prebid.server.util.StreamUtil;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Stream;

public class UsersyncMethodChooser {

    private static final String CATCH_ALL_BIDDERS = "*";

    private final Map<String, CookieSyncRequest.MethodFilter> filters;

    public UsersyncMethodChooser(CookieSyncRequest.FilterSettings filterSettings) {
        filters = initializeFilters(filterSettings);
    }

    public static UsersyncMethodChooser from(CookieSyncRequest.FilterSettings filterSettings) {
        return new UsersyncMethodChooser(filterSettings);
    }

    public Usersyncer.UsersyncMethod choose(Usersyncer usersyncer, String bidder) {
        return Stream.of(usersyncer.getPrimaryMethod(), usersyncer.getSecondaryMethod())
                .filter(method -> methodValidAndAllowed(method, bidder))
                .findFirst()
                .orElse(null);
    }

    private static Map<String, CookieSyncRequest.MethodFilter> initializeFilters(
            CookieSyncRequest.FilterSettings filterSettings) {

        if (filterSettings == null) {
            return Collections.emptyMap();
        }

        final Map<String, CookieSyncRequest.MethodFilter> filterMap = new HashMap<>();

        filterMap.computeIfAbsent(Usersyncer.UsersyncMethod.IFRAME_TYPE, key -> filterSettings.getIframe());
        filterMap.computeIfAbsent(Usersyncer.UsersyncMethod.REDIRECT_TYPE, key -> filterSettings.getImage());

        return filterMap;
    }

    private boolean methodValidAndAllowed(Usersyncer.UsersyncMethod usersyncMethod, String bidder) {
        return methodValid(usersyncMethod) && methodAllowed(usersyncMethod, bidder);
    }

    private boolean methodValid(Usersyncer.UsersyncMethod usersyncMethod) {
        return usersyncMethod != null && StringUtils.isNotBlank(usersyncMethod.getUsersyncUrl());
    }

    private boolean methodAllowed(Usersyncer.UsersyncMethod usersyncMethod, String bidder) {
        final CookieSyncRequest.MethodFilter filter = filters.get(usersyncMethod.getType());

        return filter == null || filter.getFilter() == null
                || bidderNotExcluded(bidder, filter) || bidderIncluded(bidder, filter);
    }

    private boolean bidderNotExcluded(String bidder, CookieSyncRequest.MethodFilter filter) {
        return filter.getFilter() == CookieSyncRequest.FilterType.exclude && !bidderInList(bidder, filter.getBidders());
    }

    private boolean bidderIncluded(String bidder, CookieSyncRequest.MethodFilter filter) {
        return filter.getFilter() == CookieSyncRequest.FilterType.include && bidderInList(bidder, filter.getBidders());
    }

    private boolean bidderInList(String bidder, JsonNode bidders) {
        return listMatchesAllBidders(bidders) || listContainsBidder(bidders, bidder);
    }

    private boolean listMatchesAllBidders(JsonNode bidders) {
        return bidders == null
                || bidders.isNull()
                || (bidders.isTextual() && Objects.equals(bidders.textValue(), CATCH_ALL_BIDDERS));
    }

    private boolean listContainsBidder(JsonNode bidders, String bidder) {
        return bidders.isArray() && arrayContainsString(bidders, bidder);
    }

    private static boolean arrayContainsString(JsonNode bidders, String value) {
        return StreamUtil.asStream(bidders.spliterator()).anyMatch(element -> elementEqualsString(element, value));
    }

    private static boolean elementEqualsString(JsonNode element, String value) {
        return element != null && element.isTextual() && Objects.equals(element.textValue(), value);
    }
}
