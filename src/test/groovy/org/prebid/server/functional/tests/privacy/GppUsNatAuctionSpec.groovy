package org.prebid.server.functional.tests.privacy

class GppUsNatAuctionSpec extends PrivacyBaseSpec {

    def "s"() {
        // DBABLA~CAAAAAAAAASA.QA    KnownChildSensitiveDataConsents[3]=1
        // DBABLA~CAAAqqiqqgCA.QA   SensitiveDataProcessing[1-7,9-16]=2
        // DBABLA~CAAAVUAERACA.QA   SensitiveDataProcessing[1-5,11,13,15]=1
        // DBABLA~CAAAABRREQCA.QA   SensitiveDataProcessing[6,7,9,10,12,14,16]=1
        // DBABLA~CAAAACiiIgCA.QA   SensitiveDataProcessing[6,7,9,10,12,14,16]=2
    }
}
