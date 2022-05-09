package org.prebid.server.functional.tests.multithreaded

import org.prebid.server.functional.testcontainerswip.ContainerFactory
import org.prebid.server.functional.testcontainerswip.ContainerWrapper
import org.prebid.server.functional.util.PBSUtils
import spock.lang.Specification

class BaseSpec extends Specification {

    private static final ContainerFactory containerFactory = new ContainerFactory(2)
    private static final ThreadLocal<Set<ContainerWrapper>> acquiredContainers = ThreadLocal.withInitial { [] } as ThreadLocal<Set<ContainerWrapper>>

    def cleanup() {
        releaseContainers()
    }

    protected acquireContainer(Map<String, String> config) {
        def container = containerFactory.acquireContainer(config)
        acquiredContainers.get().add(container)
        container
    }

    private void releaseContainers() {
        acquiredContainers.get()
                          .each { containerFactory.releaseContainer(it) }
                          .clear()
    }

    protected Map<String, String> getConfig() {
        [(PBSUtils.getRandomString()): PBSUtils.getRandomString()]
    }

}
