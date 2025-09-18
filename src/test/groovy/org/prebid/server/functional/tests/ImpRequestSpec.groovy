package org.prebid.server.functional.tests

import org.prebid.server.functional.model.bidder.Generic
import org.prebid.server.functional.model.bidder.Openx
import org.prebid.server.functional.model.db.StoredImp
import org.prebid.server.functional.model.request.auction.BidRequest
import org.prebid.server.functional.model.request.auction.Imp
import org.prebid.server.functional.model.request.auction.Pmp
import org.prebid.server.functional.model.request.auction.PrebidStoredRequest
import org.prebid.server.functional.service.PrebidServerService
import org.prebid.server.functional.util.PBSUtils

import static org.prebid.server.functional.model.bidder.BidderName.ALIAS
import static org.prebid.server.functional.model.bidder.BidderName.ALIAS_CAMEL_CASE
import static org.prebid.server.functional.model.bidder.BidderName.EMPTY
import static org.prebid.server.functional.model.bidder.BidderName.GENERIC
import static org.prebid.server.functional.model.bidder.BidderName.GENERIC_CAMEL_CASE
import static org.prebid.server.functional.model.bidder.BidderName.OPENX
import static org.prebid.server.functional.model.bidder.BidderName.RUBICON
import static org.prebid.server.functional.model.bidder.BidderName.UNKNOWN
import static org.prebid.server.functional.model.bidder.BidderName.WILDCARD
import static org.prebid.server.functional.model.response.auction.ErrorType.GENER_X
import static org.prebid.server.functional.model.response.auction.ErrorType.PREBID
import static org.prebid.server.functional.testcontainers.Dependencies.getNetworkServiceContainer

class ImpRequestSpec extends BaseSpec {

    private final PrebidServerService defaultPbsServiceWithAlias = pbsServiceFactory.getService(GENERIC_ALIAS_CONFIG)
    private static final String EMPTY_ID = ""

    def "PBS should update imp fields when imp.ext.prebid.imp contain bidder information"() {
        given: "Default basic BidRequest"
        def extPmp = Pmp.defaultPmp
        def storedRequestId = PBSUtils.randomString
        def bidRequest = BidRequest.defaultBidRequest.tap {
            imp.first.tap {
                pmp = Pmp.defaultPmp
                ext.prebid.storedRequest = new PrebidStoredRequest(id: storedRequestId)
                ext.prebid.imp = [(bidderName): new Imp(pmp: extPmp)]
            }
        }

        and: "Save storedImp into DB"
        def storedImp = StoredImp.getStoredImp(bidRequest).tap {
            impData = Imp.defaultImpression
        }
        storedImpDao.save(storedImp)

        when: "Requesting PBS auction"
        defaultPbsServiceWithAlias.sendAuctionRequest(bidRequest)

        then: "BidderRequest should update imp information based on imp.ext.prebid.imp value"
        def bidderRequest = bidder.getBidderRequest(bidRequest.id)
        assert bidderRequest.imp.pmp == [extPmp]

        and: "BidderRequest should contain original stored request id"
        assert bidderRequest.imp.ext.prebid.storedRequest.id == [storedRequestId]

        and: "PBS should remove imp.ext.prebid.imp from bidderRequest"
        assert bidderRequest?.imp?.ext?.prebid?.imp == [null]

        and: "PBS should remove imp.ext.prebid.bidder from bidderRequest"
        assert !bidderRequest?.imp?.first?.ext?.prebid?.bidder

        where:
        bidderName << [GENERIC, GENERIC_CAMEL_CASE]
    }

    def "PBS should update only required imp when it contain bidder information"() {
        given: "Default basic BidRequest"
        def extPmp = Pmp.defaultPmp
        def impWithParameters = Imp.defaultImpression.tap {
            pmp = Pmp.defaultPmp
            ext.prebid.imp = [(bidderName): new Imp(pmp: extPmp)]
        }
        def impWithoutParameters = Imp.defaultImpression.tap {
            pmp = Pmp.defaultPmp
        }
        def bidRequest = BidRequest.defaultBidRequest.tap {
            imp = [impWithParameters, impWithoutParameters]
        }

        when: "Requesting PBS auction"
        defaultPbsServiceWithAlias.sendAuctionRequest(bidRequest)

        then: "BidderRequest should update imp information based on imp.ext.prebid.imp value only for required imp"
        def bidderRequest = bidder.getBidderRequest(bidRequest.id)
        assert bidderRequest.imp.find { it.id == impWithParameters.id }?.pmp == extPmp
        assert bidderRequest.imp.find { it.id == impWithoutParameters.id }?.pmp == impWithoutParameters.pmp

        and: "PBS should remove imp.ext.prebid.imp from bidderRequest"
        assert !bidderRequest?.imp?.ext?.prebid?.imp

        and: "PBS should remove imp.ext.prebid.bidder from bidderRequest"
        assert !bidderRequest?.imp?.first?.ext?.prebid?.bidder

        where:
        bidderName << [GENERIC, GENERIC_CAMEL_CASE]
    }

    def "PBS should update imp fields when imp.ext.prebid.imp contain bidder alias information"() {
        given: "Default basic BidRequest"
        def extPmp = Pmp.defaultPmp
        def bidRequest = BidRequest.defaultBidRequest.tap {
            imp.first.tap {
                pmp = Pmp.defaultPmp
                ext.prebid.imp = [(aliasName): new Imp(pmp: extPmp)]
                ext.prebid.bidder.generic = null
                ext.prebid.bidder.alias = new Generic()
            }
            ext.prebid.aliases = [(aliasName.value): bidderName]
        }

        when: "Requesting PBS auction"
        defaultPbsServiceWithAlias.sendAuctionRequest(bidRequest)

        then: "BidderRequest should update imp information based on imp.ext.prebid.imp value"
        def bidderRequest = bidder.getBidderRequest(bidRequest.id)
        assert bidderRequest.imp.pmp == [extPmp]

        and: "PBS should remove imp.ext.prebid.imp from bidderRequest"
        assert !bidderRequest?.imp?.ext?.prebid?.imp

        and: "PBS should remove imp.ext.prebid.bidder from bidderRequest"
        assert !bidderRequest?.imp?.first?.ext?.prebid?.bidder

        where:
        aliasName        | bidderName
        ALIAS            | GENERIC
        ALIAS_CAMEL_CASE | GENERIC
        ALIAS            | GENERIC_CAMEL_CASE
        ALIAS_CAMEL_CASE | GENERIC_CAMEL_CASE
    }

    def "PBS should update imp fields when imp.ext.prebid.imp contain bidder alias information2"() {
        given: "Default basic BidRequest"
        def storedPmp = Pmp.defaultPmp
        def originalPmp = Pmp.defaultPmp
        def bidRequest = BidRequest.defaultBidRequest.tap {
            imp.first.tap {
                bidFloor = 15
                pmp = originalPmp
                ext.prebid.imp = [(aliasName): new Imp(pmp: storedPmp)]
                ext.prebid.bidder.generic = null
                ext.prebid.bidder.generX = new Generic()
                ext.prebid.bidder.alias = new Generic()
            }
            ext.prebid.aliases = [(GENER_X.value): bidderName,
                                  (aliasName.value): bidderName,
            ]
        }

        when: "Requesting PBS auction"
        def response = defaultPbsService.sendAuctionRequest(bidRequest)

        then: "BidderRequest should update imp information for specific alias"
        def bidderRequests = getRequests(response)
        assert bidderRequests[ALIAS.value].imp.pmp.flatten() == [storedPmp]

        and: "Left original information for other"
        assert bidderRequests[GENER_X.value].imp.pmp.flatten() == [originalPmp]


        where:
        aliasName        | bidderName
        ALIAS            | GENERIC
        ALIAS_CAMEL_CASE | GENERIC
        ALIAS            | GENERIC_CAMEL_CASE
        ALIAS_CAMEL_CASE | GENERIC_CAMEL_CASE
    }

    def "PBS shouldn't update imp fields when imp.ext.prebid.imp contain only bidder with invalid name"() {
        given: "Default basic BidRequest"
        def impPmp = Pmp.defaultPmp
        def bidRequest = BidRequest.defaultBidRequest.tap {
            imp.first.tap {
                pmp = impPmp
                ext.prebid.imp = [(bidderName): new Imp(pmp: Pmp.defaultPmp)]
            }
        }

        when: "Requesting PBS auction"
        def response = defaultPbsServiceWithAlias.sendAuctionRequest(bidRequest)

        then: "Bid response should contain warning"
        assert response.ext.warnings[PREBID]?.code == [999]
        assert response.ext.warnings[PREBID]?.message ==
                ["WARNING: request.imp[0].ext.prebid.imp.${bidderName} was dropped with the reason: invalid bidder"]

        and: "BidderRequest shouldn't update imp information based on imp.ext.prebid.imp value"
        def bidderRequest = bidder.getBidderRequest(bidRequest.id)
        assert bidderRequest.imp.pmp == [impPmp]

        and: "PBS should remove imp.ext.prebid.imp from bidderRequest"
        assert !bidderRequest?.imp?.first?.ext?.prebid?.imp

        and: "PBS should remove imp.ext.prebid.bidder from bidderRequest"
        assert !bidderRequest?.imp?.first?.ext?.prebid?.bidder

        where:
        bidderName << [WILDCARD, UNKNOWN]
    }

    def "PBS shouldn't update imp fields and without warning when imp.ext.prebid.imp contain not applicable bidder"() {
        given: "Default basic BidRequest"
        def impPmp = Pmp.defaultPmp
        def bidRequest = BidRequest.defaultBidRequest.tap {
            imp.first.tap {
                pmp = impPmp
                ext.prebid.imp = [(RUBICON): new Imp(pmp: Pmp.defaultPmp)]
            }
        }

        when: "Requesting PBS auction"
        def response = defaultPbsServiceWithAlias.sendAuctionRequest(bidRequest)

        then: "Bid response should not contain warning"
        assert !response?.ext?.warnings

        and: "BidderRequest should contain pmp from original imp"
        def bidderRequest = bidder.getBidderRequest(bidRequest.id)
        assert bidderRequest.imp.pmp == [impPmp]

        and: "PBS should remove imp.ext.prebid.imp from bidderRequest"
        assert !bidderRequest?.imp?.first?.ext?.prebid?.imp

        and: "PBS should remove imp.ext.prebid.bidder from bidderRequest"
        assert !bidderRequest?.imp?.first?.ext?.prebid?.bidder
    }

    def "PBS should always update specified bidder imp when imp.ext.prebid.imp contain such bidder"() {
        given: "PBs with openx bidder"
        def pbsService = pbsServiceFactory.getService(
                ["adapters.openx.enabled" : "true",
                 "adapters.openx.endpoint": "$networkServiceContainer.rootUri/auction".toString()])

        and: "Default basic BidRequest"
        def impPmp = Pmp.defaultPmp
        def extPrebidImpPmp = Pmp.defaultPmp
        def bidRequest = BidRequest.defaultBidRequest.tap {
            imp.first.tap {
                pmp = impPmp
                ext.prebid.bidder.openx = Openx.defaultOpenx
                ext.prebid.imp = [(OPENX): new Imp(pmp: extPrebidImpPmp)]
            }
        }

        when: "Requesting PBS auction"
        def response = pbsService.sendAuctionRequest(bidRequest)

        then: "Bid response should not contain warning"
        assert !response?.ext?.warnings

        and: "Generic bidderRequest should contain pmp from original imp"
        def bidderToBidderRequests = getRequests(response)
        assert bidderToBidderRequests[GENERIC.value].first.imp.pmp == [impPmp]

        and: "OpenX bidderRequest should contain pmp from ext.prebid.imp"
        assert bidderToBidderRequests[OPENX.value].first.imp.pmp == [extPrebidImpPmp]

        and: "PBS should remove imp.ext.prebid.bidder from bidderRequests"
        def bidderRequests = bidder.getBidderRequests(bidRequest.id)
        assert !bidderRequests?.imp?.ext?.prebid?.imp?.flatten()
    }

    def "PBS should validate imp and add proper warning when imp.ext.prebid.imp contain invalid ortb data"() {
        given: "BidRequest with invalid config for ext.prebid.imp"
        def impPmp = Pmp.defaultPmp
        def bidRequest = BidRequest.defaultBidRequest.tap {
            imp.first.tap {
                pmp = impPmp
                ext.prebid.imp = [(GENERIC): Imp.defaultImpression.tap {
                    id = EMPTY_ID
                }]
            }
        }

        when: "Requesting PBS auction"
        def response = defaultPbsServiceWithAlias.sendAuctionRequest(bidRequest)

        then: "Bid response should contain warning"
        assert response.ext.warnings[PREBID]?.code == [999]
        assert response.ext.warnings[PREBID]?.message ==
                ["imp.ext.prebid.imp.generic can not be merged into original imp [id=${bidRequest.imp.first.id}], " +
                         "reason: imp[id=] missing required field: \"id\""]

        and: "BidderRequest shouldn't update imp information based on imp.ext.prebid.imp value"
        def bidderRequest = bidder.getBidderRequest(bidRequest.id)
        assert bidderRequest.imp.pmp == [impPmp]
    }

    def "PBS shouldn't update imp fields when imp.ext.prebid.imp contain invalid empty data"() {
        given: "Default basic BidRequest"
        def impPmp = Pmp.defaultPmp
        def storedRequestId = PBSUtils.randomString
        def bidRequest = BidRequest.defaultBidRequest.tap {
            imp.first.tap {
                pmp = impPmp
                ext.prebid.imp = prebidImp
                ext.prebid.storedRequest = new PrebidStoredRequest(id: storedRequestId)
            }
        }

        and: "Save storedImp into DB"
        def storedImp = StoredImp.getStoredImp(bidRequest).tap {
            impData = Imp.defaultImpression
        }
        storedImpDao.save(storedImp)

        when: "Requesting PBS auction"
        defaultPbsServiceWithAlias.sendAuctionRequest(bidRequest)

        then: "BidderRequest shouldn't update imp information based on imp.ext.prebid.imp value"
        def bidderRequest = bidder.getBidderRequest(bidRequest.id)
        assert bidderRequest.imp.pmp == [impPmp]

        and: "BidderRequest should contain original stored request id"
        assert bidderRequest.imp.ext.prebid.storedRequest.id == [storedRequestId]

        and: "PBS should remove imp.ext.prebid.imp.pmp from bidderRequest"
        assert !bidderRequest?.imp?.first?.ext?.prebid?.imp?.get(GENERIC)?.pmp

        and: "PBS should remove imp.ext.prebid.bidder from bidderRequest"
        assert !bidderRequest?.imp?.first?.ext?.prebid?.bidder

        where:
        prebidImp << [
                null,
                [:],
                [(EMPTY): new Imp(pmp: Pmp.defaultPmp)],
                [(GENERIC): null],
                [(GENERIC): new Imp()],
                [(GENERIC): new Imp(pmp: new Pmp())]
        ]
    }
}
