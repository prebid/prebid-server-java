package org.prebid.server.functional.testcontainers

import org.prebid.server.functional.util.SystemProperties
import org.testcontainers.containers.GenericContainer

import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.locks.Condition
import java.util.concurrent.locks.Lock
import java.util.concurrent.locks.ReentrantLock

import static org.prebid.server.functional.util.SystemProperties.USE_FIXED_CONTAINER_PORTS

class ContainerFactory<T extends GenericContainer> {

    private final int maxContainers = maxContainerCount
    private final Lock registryLock = new ReentrantLock(true)
    private final Condition containerAddedOrReleasedOrStopped = registryLock.newCondition()
    private final Set<ContainerWrapper<T>> containers = []
    private final Map<ContainerWrapper<T>, AtomicInteger> containersInUse = [:]
    private final Set<ContainerWrapper<T>> stoppingContainers = []

    ContainerWrapper<T> acquireContainer(T container, Map<String, String> config) {
         guardContainerWrapperWithLock {
            if (containers.find { it.configuration == config }) {
                return getExistingContainer(config)
            } else if (containers.size() + stoppingContainers.size() < maxContainers) {
                return createContainer(container, config)
            } else {
                while (
                        containers.size() + stoppingContainers.size() >= maxContainers
                                && !containers.find { it.configuration == config }
                                && unusedContainers.isEmpty()
                ) {
                    containerAddedOrReleasedOrStopped.await()
                }

                return createOrGetExistingContainer(container, config)
            }
        }.tap {
            it.start()
        }
    }

    private ContainerWrapper<T> getExistingContainer(Map<String, String> config) {
        containers.find { it.configuration == config }.tap {
            registerAsActive(it)
        }
    }

    private ContainerWrapper<T> createContainer(T container, Map<String, String> config) {
        new ContainerWrapper<T>(container, config).tap {
            containers.add(it)
            containerAddedOrReleasedOrStopped.signalAll()
            registerAsActive(it)
        }
    }

    private ContainerWrapper createOrGetExistingContainer(T container, Map<String, String> config) {
        if (containers.find { it.configuration == config }) {
            return getExistingContainer(config)
        } else if (containers.size() + stoppingContainers.size() < maxContainers) {
            return createContainer(container, config)
        } else if (!unusedContainers.isEmpty()) {
            stopAndRemoveRandomUnusedContainer()
            return createOrGetExistingContainer(container, config)
        } else {
            throw new IllegalStateException("Should never happen")
        }
    }

    private void stopAndRemoveRandomUnusedContainer() {
        def container = unusedContainers.min { it.creationTime }
        containers.remove(container)
        stoppingContainers.add(container)

        container.stop()

        stoppingContainers.remove(container)
        containerAddedOrReleasedOrStopped.signalAll()
    }

    private void registerAsActive(ContainerWrapper containerWrapper) {
        containersInUse.computeIfAbsent(containerWrapper) { new AtomicInteger() }.incrementAndGet()
    }

    private Set<ContainerWrapper> getUnusedContainers() {
        containers - containersInUse.keySet()
    }

    void releaseContainer(ContainerWrapper container) {
        guardWithLock {
            if (!containersInUse.containsKey(container)) {
                throw new IllegalArgumentException("Invalid container: $container passed to be released")
            }
            def usersCount = containersInUse[container]
            usersCount.decrementAndGet()

            if (usersCount.get() == 0) {
                containersInUse.remove(container)
                containerAddedOrReleasedOrStopped.signalAll()
            }
        }
    }

    private def guardWithLock(Closure closure) {
        try {
            registryLock.lock()
            closure()
        } finally {
            registryLock.unlock()
        }
    }

    private ContainerWrapper<T> guardContainerWrapperWithLock(Closure<ContainerWrapper<T>> closure) {
        try {
            registryLock.lock()
            closure()
        } finally {
            registryLock.unlock()
        }
    }

    private static int getMaxContainerCount() {
        USE_FIXED_CONTAINER_PORTS
            ? 1
            : SystemProperties.getPropertyOrDefault("tests.max-container-count", 2)
    }
}
