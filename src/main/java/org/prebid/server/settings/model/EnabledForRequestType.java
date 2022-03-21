package org.prebid.server.settings.model;

import com.fasterxml.jackson.annotation.JsonAlias;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.prebid.server.metric.MetricName;

@Data
@NoArgsConstructor
@AllArgsConstructor(staticName = "of")
public class EnabledForRequestType {

    @JsonAlias("web")
    Boolean pbjs;

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
        switch (requestType) {
            case OPENRTB2_WEB:
                return pbjs;
            case OPENRTB2_APP:
                return app;
            case AMP:
                return amp;
            case VIDEO:
                return video;
            default:
                return null;
        }
    }
}
