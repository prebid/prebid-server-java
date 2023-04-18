package org.prebid.server.hooks.modules.com.confiant.adquality.model;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

import java.util.List;

@Data
public class CreativeAttributes {

    /** True if the tag is ssl, false otherwise. */
    @JsonProperty("is_ssl")
    boolean isSsl;

    /** True if any ssl error occurred. SSL errors may also occur on non-SSL tags because of potential SSL dependencies. */
    @JsonProperty("ssl_error")
    boolean sslError;

    /** Creative width. */
    int width;

    /** Creative height. */
    int height;

    /** Animation time as measured in seconds. */
    int anim;

    /** Network load before window.onload fired in KB. */
    @JsonProperty("network_load_startup")
    int networkLoadStartup;

    /** Network load after window.onload fired in KB. */
    @JsonProperty("network_load_polite")
    int networkLoadPolite;

    /** Key/value object containing VAST (video) creative attributes {@link VASTAttributes}. */
    VASTAttributes vast;

    /** Array containing a list of brands. */
    List<String> brands;

    /** Array containing a list of categories names and codes {@link CreativeCategory}. See the Appendix for the full list of categories. */
    List<CreativeCategory> categories;
}


