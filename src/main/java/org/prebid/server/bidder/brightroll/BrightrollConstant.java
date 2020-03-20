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

    public static final List<Integer> BLOCKED_CREATIVETYPES_FOR_BUSINESSINSIDER = Arrays.asList(1, 2, 3, 6, 9, 10);
    public static final List<String> BLOCKED_CATEGORIES_FOR_BUSINESSINSIDER =
            Arrays.asList("IAB8-5", "IAB8-18", "IAB15-1");
    public static final List<String> BLOCKED_ADVERTISERS_FOR_BUSINESSINSIDER =
            Arrays.asList("1smartpenny.com", "advantagegold.com", "beverlyhillsmd.com", "beyonddiet.com",
                        "fisheradviser.com", "fisherinvestments.com", "fisherretirementtips.com", "fool.com",
                        "freescore360.com", "gruener-fisher.de", "instantcheckmate.com", "intercreditreport.com",
                        "king.com", "ladyfitnessandhealth.com", "ladyfitnessutah.com", "livecellresearch.com",
                        "lowermybills.com", "promeritumgroup.com", "righttobear.com", "slendertone.com",
                        "Squattypotty.com", "thebeverlyhillsmdsolution.com", "thecrux.com", "thehornnews.com");

}
