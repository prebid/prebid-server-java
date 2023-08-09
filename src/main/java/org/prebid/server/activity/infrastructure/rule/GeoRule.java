package org.prebid.server.activity.infrastructure.rule;

import lombok.Value;
import org.apache.commons.lang3.StringUtils;
import org.prebid.server.activity.ComponentType;
import org.prebid.server.activity.infrastructure.debug.Loggable;
import org.prebid.server.activity.infrastructure.payload.ActivityInvocationPayload;
import org.prebid.server.activity.infrastructure.payload.GeoActivityInvocationPayload;
import org.prebid.server.activity.infrastructure.payload.GpcActivityInvocationPayload;

import java.util.List;
import java.util.Set;

public final class GeoRule extends AbstractMatchRule implements Loggable {

    private final Set<ComponentType> componentTypes;
    private final Set<String> componentNames;
    private final boolean sidsMatched;
    private final List<GeoCode> geoCodes;
    private final String gpc;
    private final boolean allowed;

    public GeoRule(Set<ComponentType> componentTypes,
                   Set<String> componentNames,
                   boolean sidsMatched,
                   List<GeoCode> geoCodes,
                   String gpc,
                   boolean allowed) {

        this.componentTypes = componentTypes;
        this.componentNames = componentNames;
        this.sidsMatched = sidsMatched;
        this.geoCodes = geoCodes;
        this.gpc = gpc;
        this.allowed = allowed;
    }

    @Override
    public boolean matches(ActivityInvocationPayload activityInvocationPayload) {
        return sidsMatched
                && (geoCodes == null || matchesOneOfGeoCodes(activityInvocationPayload))
                && (gpc == null || matchesGpc(activityInvocationPayload))
                && (componentTypes == null || componentTypes.contains(activityInvocationPayload.componentType()))
                && (componentNames == null || componentNames.contains(activityInvocationPayload.componentName()));
    }

    private boolean matchesOneOfGeoCodes(ActivityInvocationPayload activityInvocationPayload) {
        if (activityInvocationPayload instanceof GeoActivityInvocationPayload geoPayload) {
            return geoCodes.stream().anyMatch(geoCode -> matchesGeoCode(geoCode, geoPayload));
        }

        return true;
    }

    private static boolean matchesGeoCode(GeoCode geoCode, GeoActivityInvocationPayload geoPayload) {
        final String region = geoCode.getRegion();
        return StringUtils.equalsIgnoreCase(geoCode.getCountry(), geoPayload.country())
                && (region == null || StringUtils.equalsIgnoreCase(region, geoPayload.region()));
    }

    private boolean matchesGpc(ActivityInvocationPayload activityInvocationPayload) {
        if (activityInvocationPayload instanceof GpcActivityInvocationPayload gpcActivityInvocationPayload) {
            return gpc.equals(gpcActivityInvocationPayload.gpc());
        }

        return true;
    }

    @Override
    public boolean allowed() {
        return allowed;
    }

    @Override
    public Object asLogEntry() {
        return new GeoRuleLogEntry(componentTypes, componentNames, sidsMatched, geoCodes, gpc, allowed);
    }

    @Value(staticConstructor = "of")
    public static class GeoCode {

        String country;

        String region;
    }

    private record GeoRuleLogEntry(Set<ComponentType> componentTypes,
                                   Set<String> componentNames,
                                   boolean gppSidsMatched,
                                   List<GeoCode> geoCodes,
                                   String gpc,
                                   boolean allow) {
    }
}
