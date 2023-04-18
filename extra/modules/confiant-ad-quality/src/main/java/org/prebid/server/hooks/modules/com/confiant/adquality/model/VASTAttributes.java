package org.prebid.server.hooks.modules.com.confiant.adquality.model;

import lombok.Data;

@Data
public class VASTAttributes {

    /** Number of VAST redirects (or null if not VAST). Zero if no redirect. */
    int redirects;
}
