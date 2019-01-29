package org.prebid.server.spring.config.bidder.model;

import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.validation.annotation.Validated;

import javax.validation.constraints.NotBlank;
import javax.validation.constraints.NotNull;
import java.util.List;

@Validated
@Data
@NoArgsConstructor
public class MetaInfo {

    @NotBlank
    private String maintainerEmail;

    private List<String> appMediaTypes;

    private List<String> siteMediaTypes;

    private List<String> supportedVendors;

    @NotNull
    private Integer vendorId;
}
