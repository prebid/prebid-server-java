package com.iab.openrtb.request.ntv;

/**
 * Options for the various bid response protocols that could be supported by an exchange.
 */
public enum Protocol {
    /**
     * VAST 1.0
     */
    VAST10(1),
    /**
     * VAST 2.0
     */
    VAST20(2),
    /**
     * VAST 3.0
     */
    VAST30(3),
    /**
     * VAST 1.0 Wrapper
     */
    VAST10_WRAPPER(4),
    /**
     * VAST 2.0 Wrapper
     */
    VAST20_WRAPPER(5),
    /**
     * VAST 1.0 Wrapper
     */
    VAST30_WRAPPER(6),
    /**
     * VAST 4.0
     */
    VAST40(7),
    /**
     * VAST 4.0 Wrapper
     */
    VAST40_WRAPPER(8),
    /**
     * DAAST 1.0
     */
    DAAST10(9),
    /**
     * DAAST 1.0 Wrapper
     */
    DAAST10_WRAPPER(10);


    private final Integer value;

    Protocol(final Integer value) {
        this.value = value;
    }

    public Integer getValue() {
        return value;
    }
}
