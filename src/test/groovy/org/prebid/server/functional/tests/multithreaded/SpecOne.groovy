package org.prebid.server.functional.tests.multithreaded

class SpecOne extends BaseSpec {

    def "one"() {
        setup:
        def container = acquireContainer(getConfig())

        expect:
        println "$container.creationTime $container.uuid $container.configuration"
        assert container
    }

    def "two"() {
        setup:
        def container = acquireContainer(getConfig())

        expect:
        println "$container.creationTime $container.uuid $container.configuration"
        assert container
    }

    def "three"() {
        setup:
        def container = acquireContainer(getConfig())

        expect:
        println "$container.creationTime $container.uuid $container.configuration"
        assert container
    }

    def "four"() {
        setup:
        def container = acquireContainer(getConfig())

        expect:
        println "$container.creationTime $container.uuid $container.configuration"
        assert container
    }

    def "five"() {
        setup:
        def container = acquireContainer(getConfig())

        expect:
        println "$container.creationTime $container.uuid $container.configuration"
        assert container
    }
}
