package org.prebid.server.privacy.gdpr.model;

public enum TcfPurpose {

    /**
     * Vendors can:
     * <li>Store and access information on the device such as cookies and device identifiers presented to a user</li>
     */
    informationStorageAndAccess(1),

    /**
     * To do basic ad selection vendors can:
     * <li>Use real-time information about the context in which the ad will be shown, to show the ad,
     * including information about the content and the device,
     * such as: device type and capabilities, user agent, URL, IP address</li>
     * <li>Use a user’s non-precise geolocation data</li>
     * <li>Control the frequency of ads shown to a user.</li>
     * <li>Sequence the order in which ads are shown to a user.</li>
     * <li>Prevent an ad from serving in an unsuitable editorial (brand-unsafe) context.</li>
     * <br>
     * Vendors cannot:
     * <li>Create a personalised ads profile using this information for the selection of future ads.</li>
     * <br>
     * N.B. Non-precise means only an approximate location involving at least a radius of 500 meters is permitted
     */
    selectBasicAds(2),

    /**
     * To select personalised ads vendors can:
     * <li> Select personalised ads based on a user profile or other historical user data,
     * including a user’s prior activity, interests, visits to sites or apps, location, or demographic information.</li>
     */
    selectPersonalisedAds(4),

    /**
     * To measure ad performance vendors can:
     * <li>Measure whether and how ads were delivered to and interacted with by a user</li>
     * <li>Provide reporting about ads including their effectiveness and performance</li>
     * <li>Provide reporting about users who interacted with ads using data observed during the course
     * of the user's interaction with that ad</li>
     * <li>Provide reporting to publishers about the ads displayed on their property</li>
     * <li>Measure whether an ad is serving in a suitable editorial environment (brand-safe) context</li>
     * <li>Determine the percentage of the ad that had the opportunity to be seen and the duration
     * of that opportunity</li>
     * <li>Combine this information with other information previously collected,
     * including from across websites and apps</li>
     * <br>
     * Vendors cannot:
     * <li>Apply panel- or similarly-derived audience insights data to ad measurement data without a Legal Basis
     * to apply market research to generate audience insights (Purpose 9)</li>
     */
    measureAdPerformance(7);

    private int id;

    TcfPurpose(int id) {
        this.id = id;
    }

    public int getId() {
        return id;
    }
}
