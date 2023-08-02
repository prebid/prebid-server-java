package org.prebid.server.activity.infrastructure.rule;

import lombok.Value;
import org.apache.commons.lang3.StringUtils;
import org.prebid.server.activity.ComponentType;
import org.prebid.server.activity.infrastructure.payload.ActivityInvocationPayload;
import org.prebid.server.activity.infrastructure.payload.GeoActivityInvocationPayload;
import org.prebid.server.activity.infrastructure.payload.GpcActivityInvocationPayload;

import java.util.List;
import java.util.Set;

public final class GeoRule extends AbstractMatchRule {

    private final ComponentRule componentRule;
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

        this.componentRule = new ComponentRule(componentTypes, componentNames, allowed);
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
                && componentRule.matches(activityInvocationPayload);
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

    @Value(staticConstructor = "of")
    public static class GeoCode {

        String country;

        String region;
    }
}
