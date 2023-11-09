package org.prebid.server.deals.targeting.syntax;

import lombok.EqualsAndHashCode;
import org.apache.commons.lang3.StringUtils;
import org.prebid.server.exception.TargetingSyntaxException;

import java.util.Arrays;
import java.util.EnumSet;
import java.util.Objects;

@EqualsAndHashCode
public class TargetingCategory {

    private static final String BIDDER_PARAM_PATH_PATTERN = "\\w+(\\.\\w+)+";

    private static final EnumSet<Type> DYNAMIC_TYPES = EnumSet.of(
            Type.deviceGeoExt,
            Type.deviceExt,
            Type.bidderParam,
            Type.userSegment,
            Type.userFirstPartyData,
            Type.siteFirstPartyData);

    private static final EnumSet<Type> STATIC_TYPES = EnumSet.complementOf(DYNAMIC_TYPES);

    private final Type type;
    private final String path;

    public TargetingCategory(Type type) {
        this(type, null);
    }

    public TargetingCategory(Type type, String path) {
        this.type = Objects.requireNonNull(type);
        this.path = path;
    }

    public static boolean isTargetingCategory(String candidate) {
        final boolean isSimpleCategory = STATIC_TYPES.stream().anyMatch(op -> op.attribute().equals(candidate));
        return isSimpleCategory || DYNAMIC_TYPES.stream().anyMatch(op -> candidate.startsWith(op.attribute()));
    }

    public static TargetingCategory fromString(String candidate) {
        for (final Type type : STATIC_TYPES) {
            if (type.attribute().equals(candidate)) {
                return new TargetingCategory(type);
            }
        }

        for (final Type type : DYNAMIC_TYPES) {
            if (candidate.startsWith(type.attribute())) {
                return parseDynamicCategory(candidate, type);
            }
        }

        throw new IllegalArgumentException("Unrecognized targeting category: " + candidate);
    }

    private static TargetingCategory parseDynamicCategory(String candidate, Type type) {
        return switch (type) {
            case deviceGeoExt, deviceExt, userSegment, userFirstPartyData, siteFirstPartyData ->
                    parseByTypeAttribute(candidate, type);
            case bidderParam -> parseBidderParam(candidate, type);
            default -> throw new IllegalStateException("Unexpected dynamic targeting category type " + type);
        };
    }

    private static TargetingCategory parseByTypeAttribute(String candidate, Type type) {
        final String candidatePath = StringUtils.substringAfter(candidate, type.attribute());
        return new TargetingCategory(type, candidatePath);
    }

    private static TargetingCategory parseBidderParam(String candidate, Type type) {
        final String candidatePath = StringUtils.substringAfter(candidate, type.attribute());
        if (candidatePath.matches(BIDDER_PARAM_PATH_PATTERN)) {
            return new TargetingCategory(type, dropBidderName(candidatePath));
        } else {
            throw new TargetingSyntaxException("BidderParam path is incorrect: " + candidatePath);
        }
    }

    private static String dropBidderName(String path) {
        final int index = path.indexOf('.');
        return path.substring(index + 1);
    }

    public Type type() {
        return type;
    }

    public String path() {
        return path;
    }

    public enum Type {
        size("adunit.size"),
        mediaType("adunit.mediatype"),
        adslot("adunit.adslot"),
        domain("site.domain"),
        publisherDomain("site.publisher.domain"),
        referrer("site.referrer"),
        appBundle("app.bundle"),
        deviceGeoExt("device.geo.ext."),
        deviceExt("device.ext."),
        pagePosition("pos"),
        location("geo.distance"),
        bidderParam("bidp."),
        userSegment("segment."),
        userFirstPartyData("ufpd."),
        siteFirstPartyData("sfpd."),
        dow("user.ext.time.userdow"),
        hour("user.ext.time.userhour");

        private final String attribute;

        Type(String attribute) {
            this.attribute = attribute;
        }

        public String attribute() {
            return attribute;
        }

        public static Type fromString(String attribute) {
            return Arrays.stream(values())
                    .filter(value -> value.attribute.equals(attribute))
                    .findFirst()
                    .orElseThrow(() -> new IllegalArgumentException(
                            "Unrecognized targeting category type: " + attribute));
        }
    }
}
