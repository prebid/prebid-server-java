package org.prebid.server.settings.model;

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

    /**
     * Tells if gdpr is enabled for request type defined in {@param requestType}.
     * Returns null if request type is unknown or null.
     */
    public Boolean isEnabledFor(MetricName requestType) {
        if (requestType == null) {
            return null;
        }
        if (MetricName.openrtb2web.equals(requestType)) {
            return web;
        } else if (MetricName.amp.equals(requestType)) {
            return amp;
        } else if (MetricName.openrtb2app.equals(requestType)) {
            return app;
        } else if (MetricName.video.equals(requestType)) {
            return video;
        } else {
            return null;
        }
    }
}
