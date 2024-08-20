package org.prebid.server.activity.infrastructure.rule;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.Value;
import org.apache.commons.lang3.StringUtils;
import org.prebid.server.activity.infrastructure.debug.Loggable;
import org.prebid.server.activity.infrastructure.payload.CompositeActivityInvocationPayload;
import org.prebid.server.activity.infrastructure.payload.GeoActivityInvocationPayload;

import java.util.List;

public class GeoRule extends AbstractMatchRule implements Loggable {

    private final boolean isSidsMatched;
    private final List<GeoCode> geoCodes;
    private final boolean isAllowed;

    public GeoRule(boolean isSidsMatched, List<GeoCode> geoCodes, boolean isAllowed) {
        this.isSidsMatched = isSidsMatched;
        this.geoCodes = geoCodes;
        this.isAllowed = isAllowed;
    }

    @Override
    public boolean matches(CompositeActivityInvocationPayload payload) {
        if (!payload.hasPayload(GeoActivityInvocationPayload.class)) {
            return true;
        }

        final GeoActivityInvocationPayload geo = payload.get(GeoActivityInvocationPayload.class);
        return isSidsMatched && (geoCodes == null || matchesOneOfGeoCodes(geo));
    }

    private boolean matchesOneOfGeoCodes(GeoActivityInvocationPayload geoPayload) {
        return geoCodes.stream().anyMatch(geoCode -> matchesGeoCode(geoCode, geoPayload));
    }

    private static boolean matchesGeoCode(GeoCode geoCode, GeoActivityInvocationPayload geoPayload) {
        final String region = geoCode.getRegion();
        return StringUtils.equalsIgnoreCase(geoCode.getCountry(), geoPayload.country())
                && (region == null || StringUtils.equalsIgnoreCase(region, geoPayload.region()));
    }

    @Override
    public boolean isAllowed() {
        return isAllowed;
    }

    @Override
    public JsonNode asLogEntry(ObjectMapper mapper) {
        return mapper.valueToTree(new GeoRuleLogEntry(isSidsMatched, geoCodes, isAllowed));
    }

    @Value(staticConstructor = "of")
    public static class GeoCode {

        String country;

        String region;
    }

    private record GeoRuleLogEntry(boolean gppSidsMatched,
                                   List<GeoCode> geoCodes,
                                   boolean allow) {
    }
}
