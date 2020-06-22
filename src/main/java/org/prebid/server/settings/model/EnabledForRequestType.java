package org.prebid.server.settings.model;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.prebid.server.metric.MetricName;

@Data
@NoArgsConstructor
@AllArgsConstructor(staticName = "of")
public class EnabledForRequestType {

    Boolean web;

    Boolean amp;

    Boolean app;

    Boolean video;

    @JsonProperty("cookie-sync")
    Boolean cookieSync;

    @JsonProperty("set-uid")
    Boolean setUId;

    /**
     * Tells if gdpr is enabled for request type defined in {@param metricName}.
     * Returns null if request type is unknown or null.
     */
    public Boolean isEnabledFor(MetricName metricName) {
        if (metricName == null) {
            return null;
        }
        if (MetricName.openrtb2web.equals(metricName)) {
            return web;
        } else if (MetricName.amp.equals(metricName)) {
            return amp;
        } else if (MetricName.openrtb2app.equals(metricName)) {
            return app;
        } else if (MetricName.video.equals(metricName)) {
            return video;
        } else if (MetricName.cookie_sync.equals(metricName)) {
            return cookieSync;
        } else if (MetricName.setuid.equals(metricName)) {
            return setUId;
        } else {
            return null;
        }
    }
}
