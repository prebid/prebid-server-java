package org.prebid.server.spring.config.bidder.model;

import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.validation.annotation.Validated;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.util.List;

@Validated
@Data
@NoArgsConstructor
public class MetaInfo {

    @NotBlank
    private String maintainerEmail;

    private List<MediaType> appMediaTypes;

    private List<MediaType> siteMediaTypes;

    private List<MediaType> doohMediaTypes;

    private List<String> supportedVendors;

    @NotNull
    private Integer vendorId;
}
