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
            Type.DEVICE_GEO_EXT,
            Type.DEVICE_EXT,
            Type.BIDDER_PARAM,
            Type.USER_SEGMENT,
            Type.USER_FIRST_PARTY_DATA,
            Type.SITE_FIRST_PARTY_DATA);

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

        throw new IllegalArgumentException(String.format("Unrecognized targeting category: %s", candidate));
    }

    private static TargetingCategory parseDynamicCategory(String candidate, Type type) {
        switch (type) {
            case DEVICE_GEO_EXT:
            case DEVICE_EXT:
            case USER_SEGMENT:
            case USER_FIRST_PARTY_DATA:
            case SITE_FIRST_PARTY_DATA:
                return parseByTypeAttribute(candidate, type);
            case BIDDER_PARAM:
                return parseBidderParam(candidate, type);
            default:
                throw new IllegalStateException(
                        String.format("Unexpected dynamic targeting category type %s", type));
        }
    }

    private static TargetingCategory parseByTypeAttribute(String candidate, Type type) {
        final String candidatePath = StringUtils.substringAfter(candidate, type.attribute());
        return new TargetingCategory(type, candidatePath);
    }

    private static TargetingCategory parseBidderParam(String candidate, Type type) {
        final String candidatePath = StringUtils.substringAfter(candidate, type.attribute());
        if (candidatePath.matches(BIDDER_PARAM_PATH_PATTERN)) {
            return new TargetingCategory(type, candidatePath);
        } else {
            throw new TargetingSyntaxException(
                    String.format("BidderParam path is incorrect: %s", candidatePath));
        }
    }

    public Type type() {
        return type;
    }

    public String path() {
        return path;
    }

    public enum Type {
        SIZE("adunit.size"),
        MEDIA_TYPE("adunit.mediatype"),
        ADSLOT("adunit.adslot"),
        DOMAIN("site.domain"),
        PUBLISHER_DOMAIN("site.publisher.domain"),
        REFERRER("site.referrer"),
        APP_BUNDLE("app.bundle"),
        DEVICE_GEO_EXT("device.geo.ext."),
        DEVICE_EXT("device.ext."),
        PAGE_POSITION("pos"),
        LOCATION("geo.distance"),
        BIDDER_PARAM("bidp."),
        USER_SEGMENT("segment."),
        USER_FIRST_PARTY_DATA("ufpd."),
        SITE_FIRST_PARTY_DATA("sfpd."),
        DOW("user.ext.time.userdow"),
        HOUR("user.ext.time.userhour");

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
                            String.format("Unrecognized targeting category type: %s", attribute)));
        }
    }
}
