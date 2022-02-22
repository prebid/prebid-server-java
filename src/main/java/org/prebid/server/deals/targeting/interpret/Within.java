package org.prebid.server.deals.targeting.interpret;

import lombok.EqualsAndHashCode;
import org.prebid.server.deals.targeting.RequestContext;
import org.prebid.server.deals.targeting.model.GeoLocation;
import org.prebid.server.deals.targeting.model.GeoRegion;
import org.prebid.server.deals.targeting.syntax.TargetingCategory;

import java.util.Objects;

@EqualsAndHashCode
public class Within implements TerminalExpression {

    private static final int EARTH_RADIUS_MI = 3959;

    private final TargetingCategory category;

    private final GeoRegion value;

    public Within(TargetingCategory category, GeoRegion value) {
        this.category = Objects.requireNonNull(category);
        this.value = Objects.requireNonNull(value);
    }

    @Override
    public boolean matches(RequestContext context) {
        final GeoLocation location = context.lookupGeoLocation(category);

        return location != null && isLocationWithinRegion(location);
    }

    private boolean isLocationWithinRegion(GeoLocation location) {
        final double distance = calculateDistance(location.getLat(), location.getLon(), value.getLat(), value.getLon());

        return value.getRadiusMiles() > distance;
    }

    private static double calculateDistance(double startLat, double startLong, double endLat, double endLong) {
        final double dLat = Math.toRadians(endLat - startLat);
        final double dLong = Math.toRadians(endLong - startLong);

        double a = Math.pow(Math.sin(dLat / 2), 2)
                + Math.cos(Math.toRadians(startLat)) * Math.cos(Math.toRadians(endLat))
                * Math.pow(Math.sin(dLong / 2), 2);
        double c = 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));

        return EARTH_RADIUS_MI * c;
    }
}
