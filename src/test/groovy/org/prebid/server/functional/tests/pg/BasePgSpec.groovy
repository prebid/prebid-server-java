package org.prebid.server.functional.tests.pg

import org.prebid.server.functional.model.deals.userdata.UserDetailsResponse
import org.prebid.server.functional.model.request.dealsupdate.ForceDealsUpdateRequest
import org.prebid.server.functional.service.PrebidServerService
import org.prebid.server.functional.testcontainers.Dependencies
import org.prebid.server.functional.testcontainers.PBSTest
import org.prebid.server.functional.testcontainers.PbsPgConfig
import org.prebid.server.functional.testcontainers.PbsServiceFactory
import org.prebid.server.functional.testcontainers.scaffolding.Bidder
import org.prebid.server.functional.testcontainers.scaffolding.pg.Alert
import org.prebid.server.functional.testcontainers.scaffolding.pg.DeliveryStatistics
import org.prebid.server.functional.testcontainers.scaffolding.pg.GeneralPlanner
import org.prebid.server.functional.testcontainers.scaffolding.pg.UserData
import org.prebid.server.functional.util.ObjectMapperWrapper
import org.prebid.server.functional.util.PBSUtils
import spock.lang.Specification

@PBSTest
// TODO migrate this to extend BaseSpec
abstract class BasePgSpec extends Specification {

    protected static final ObjectMapperWrapper mapper = Dependencies.objectMapperWrapper
    protected static final PbsServiceFactory pbsServiceFactory = new PbsServiceFactory(Dependencies.networkServiceContainer, mapper)

    protected static final GeneralPlanner generalPlanner = new GeneralPlanner(Dependencies.networkServiceContainer, mapper)
    protected static final DeliveryStatistics deliveryStatistics = new DeliveryStatistics(Dependencies.networkServiceContainer, mapper)
    protected static final Alert alert = new Alert(Dependencies.networkServiceContainer, mapper)
    protected static final UserData userData = new UserData(Dependencies.networkServiceContainer, mapper)

    protected static final PbsPgConfig pgConfig = new PbsPgConfig(Dependencies.networkServiceContainer)
    protected static final PrebidServerService pgPbsService = pbsServiceFactory.getService(pgConfig.properties)
    protected static final Bidder bidder = new Bidder(Dependencies.networkServiceContainer, mapper)

    def setupSpec() {
        generalPlanner.setResponse()

        deliveryStatistics.setResponse()

        alert.setResponse()

        userData.setResponse()
        userData.setUserDataResponse(UserDetailsResponse.defaultUserResponse)
    }

    def cleanupSpec() {
        pgPbsService.sendForceDealsUpdateRequest(ForceDealsUpdateRequest.invalidateLineItemsRequest)
        pgPbsService.sendForceDealsUpdateRequest(ForceDealsUpdateRequest.createReportRequest)
        pgPbsService.sendForceDealsUpdateRequest(ForceDealsUpdateRequest.sendReportRequest)
    }

    protected static void updateLineItemsAndWait() {
        def initialPlansRequestCount = generalPlanner.recordedPlansRequestCount
        pgPbsService.sendForceDealsUpdateRequest(ForceDealsUpdateRequest.updateLineItemsRequest)
        PBSUtils.waitUntil { generalPlanner.recordedPlansRequestCount == initialPlansRequestCount + 1 }
    }
}
