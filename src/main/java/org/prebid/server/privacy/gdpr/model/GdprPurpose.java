package org.prebid.server.privacy.gdpr.model;

public enum GdprPurpose {

    /**
     * The storage of information, or access to information that is already stored, on your device
     * such as advertising identifiers, device identifiers, cookies, and similar technologies.
     */
    informationStorageAndAccess(1),

    /**
     * The collection of information, and combination with previously collected information,
     * to select and deliver advertisements for you, and to measure the delivery and effectiveness of
     * such advertisements. This includes using previously collected information about your interests
     * to select ads, processing data about what advertisements were shown, how often they were shown,
     * when and where they were shown, and whether you took any action related to the advertisement,
     * including for example clicking an ad or making a purchase. This does not include personalisation,
     * which is the collection and processing of information about your use of this service to subsequently
     * personalise advertising and/or content for you in other contexts, such as websites or apps, over time.
     */
    adSelectionAndDeliveryAndReporting(3);

    private int id;

    GdprPurpose(int id) {
        this.id = id;
    }

    public int getId() {
        return id;
    }
}
