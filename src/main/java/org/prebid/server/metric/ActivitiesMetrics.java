package org.prebid.server.metric;

import com.codahale.metrics.MetricRegistry;
import io.netty.util.internal.StringUtil;
import org.prebid.server.activity.Activity;

import java.util.EnumMap;
import java.util.Map;
import java.util.function.Function;

public class ActivitiesMetrics extends UpdatableMetrics {

    private final Function<Activity, UpdatableMetrics> disallowedActivityMetricCreator;

    private final Map<Activity, UpdatableMetrics> disallowedActivityMetrics;

    ActivitiesMetrics(MetricRegistry metricRegistry, CounterType counterType, String prefix) {
        super(metricRegistry, counterType, nameCreator(prefix, StringUtil.EMPTY_STRING));

        disallowedActivityMetricCreator = activity -> new UpdatableMetrics(
                metricRegistry,
                counterType,
                nameCreator(prefix, suffixFromActivity(activity)));

        disallowedActivityMetrics = new EnumMap<>(Activity.class);
    }

    private static Function<MetricName, String> nameCreator(String prefix, String suffix) {
        return metricName -> "%s.activity.%s%s".formatted(prefix, suffix, metricName);
    }

    private static String suffixFromActivity(Activity activity) {
        return switch (activity) {
            case SYNC_USER -> "sync_user";
            case CALL_BIDDER -> "fetch_bids";
            case MODIFY_UFDP -> "enrich_ufpd";
            case TRANSMIT_UFPD -> "transmit_ufpd";
            case TRANSMIT_GEO -> "transmit_precise_geo";
            case TRANSMIT_TID -> "transmit_tid";
            case REPORT_ANALYTICS -> "report_analytics";
        } + ".";
    }

    UpdatableMetrics forActivity(Activity activity) {
        return disallowedActivityMetrics.computeIfAbsent(activity, disallowedActivityMetricCreator);
    }
}
