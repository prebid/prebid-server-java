package org.prebid.server.bidder.brightroll;

import io.vertx.core.http.HttpHeaders;

import java.util.Arrays;
import java.util.List;

/**
 * Brightroll bidder constants
 * @author smithaa
 */
public class BrightrollConstant {
    private BrightrollConstant() {
    }

    public static final String VERSION = "2.5";
    public static final CharSequence OPEN_RTB_VERSION_HEADER = HttpHeaders.createOptimized("x-openrtb-version");

    public static final List<Integer> BLOCKED_CREATIVETYPES_FOR_BUSINESSINSIDER =
            Arrays.asList(17, 1, 3, 8, 9, 10, 13, 14);
    public static final List<String> BLOCKED_CATEGORIES_FOR_BUSINESSINSIDER =
            Arrays.asList("IAB7", "IAB7-39", "IAB7-44", "IAB9-30", "IAB11", "IAB13-2", "IAB14-1", "IAB15-1 ,IAB15-5",
                    "IAB17-18", "IAB18-1", "IAB18-2", "IAB7-19", "IAB19-30", "IAB23", "IAB25-7", "IAB26-1", "IAB26-2",
                    "IAB26-3", "IAB26-4");
    public static final List<String> BLOCKED_ADVERTISERS_FOR_BUSINESSINSIDER =
            Arrays.asList("1smartpenny.com", "advantagegold.com", "beverlyhillsmd.com", "beyonddiet.com",
                        "fisheradviser.com", "fisherinvestments.com", "fisherretirementtips.com", "fool.com",
                        "freescore360.com", "gruener-fisher.de", "instantcheckmate.com", "intercreditreport.com",
                        "king.com", "ladyfitnessandhealth.com", "ladyfitnessutah.com", "livecellresearch.com",
                        "lowermybills.com", "promeritumgroup.com", "righttobear.com", "slendertone.com",
                        "Squattypotty.com", "thebeverlyhillsmdsolution.com", "thecrux.com", "thehornnews.com");
}
