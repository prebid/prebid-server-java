package org.prebid.server.functional.pg

import org.prebid.server.functional.model.deals.userdata.UserDetailsResponse
import org.prebid.server.functional.model.request.dealsupdate.ForceDealsUpdateRequest
import org.prebid.server.functional.service.PrebidServerService
import org.prebid.server.functional.testcontainers.PBSTest
import org.prebid.server.functional.testcontainers.PbsContainerProperties
import org.prebid.server.functional.testcontainers.PbsPgConfig
import org.prebid.server.functional.testcontainers.PbsServiceFactory
import org.prebid.server.functional.testcontainers.scaffolding.Bidder
import org.prebid.server.functional.testcontainers.scaffolding.pg.Alert
import org.prebid.server.functional.testcontainers.scaffolding.pg.DeliveryStatistics
import org.prebid.server.functional.testcontainers.scaffolding.pg.GeneralPlanner
import org.prebid.server.functional.testcontainers.scaffolding.pg.UserData
import org.prebid.server.functional.util.ObjectMapperWrapper
import spock.lang.Specification

import static org.prebid.server.functional.testcontainers.Dependencies.networkServiceContainer
import static org.prebid.server.functional.testcontainers.Dependencies.objectMapperWrapper

@PBSTest
abstract class BasePgSpec extends Specification {

    protected static final ObjectMapperWrapper mapper = objectMapperWrapper
    protected static final PbsServiceFactory pbsServiceFactory = new PbsServiceFactory(networkServiceContainer, mapper)

    protected static final GeneralPlanner generalPlanner = new GeneralPlanner(networkServiceContainer, mapper).tap {
        setResponse()
    }
    protected static final DeliveryStatistics deliveryStatistics = new DeliveryStatistics(networkServiceContainer, mapper)
    protected static final Alert alert = new Alert(networkServiceContainer, mapper)
    protected static final UserData userData = new UserData(networkServiceContainer, mapper)

    private static final Map<String, String> pgPbsConfig = pbsServiceFactory.generalSettings() +
            PbsPgConfig.getPgConfig(networkServiceContainer)
    protected static final PrebidServerService pgPbsService = pbsServiceFactory.getService(pgPbsConfig)
    protected static final PbsContainerProperties pgPbsProperties = new PbsContainerProperties(pbsServiceFactory.getContainer(pgPbsConfig))
    protected static final Bidder bidder = new Bidder(networkServiceContainer, mapper)

    def setupSpec() {
        generalPlanner.setResponse()

        deliveryStatistics.setResponse()

        alert.setResponse()

        userData.setResponse()
        userData.setUserDataResponse(UserDetailsResponse.defaultUserResponse)
    }

    def cleanupSpec() {
        pgPbsService.sendForceDealsUpdateRequest(ForceDealsUpdateRequest.invalidateLineItemsRequest)
    }
}
