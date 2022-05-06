package org.prebid.server.functional

import groovy.transform.EqualsAndHashCode

import java.time.Instant
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.locks.Condition
import java.util.concurrent.locks.Lock
import java.util.concurrent.locks.ReentrantLock

class ContainerFactory {

    private final int maxContainers
    private final Lock registryLock = new ReentrantLock(true)
    private final Condition containerAddedOrReleasedOrStopped = registryLock.newCondition()
    private final Set<ContainerWrapper> containers = []
    private final Map<ContainerWrapper, AtomicInteger> containersInUse = [:]
    private final Set<ContainerWrapper> stoppingContainers = []

    ContainerFactory(int maxContainers = 2) {
        this.maxContainers = maxContainers
    }

    ContainerWrapper acquireContainer(Map<String, String> config) {
        return guardWithLock {
            if (containers.find { it.configuration == config }) {
                return getExistingContainer(config)
            } else if (containers.size() + stoppingContainers.size() < maxContainers) {
                return createContainer(config)
            } else {
                while (
                        containers.size() + stoppingContainers.size() >= maxContainers
                                && !containers.find { it.configuration == config }
                                && unusedContainers.isEmpty()
                ) {
                    containerAddedOrReleasedOrStopped.await()
                }

                return createOrGetExistingContainer(config)
            }
        }.tap {
            it.start()
        }
    }

    private ContainerWrapper getExistingContainer(Map<String, String> config) {
        containers.find { it.configuration == config }.tap {
            registerAsActive(it)
        }
    }

    private ContainerWrapper createContainer(Map<String, String> config) {
        new ContainerWrapper(config).tap {
            containers.add(it)
            containerAddedOrReleasedOrStopped.signalAll()
            registerAsActive(it)
        }
    }

    private ContainerWrapper createOrGetExistingContainer(Map<String, String> config) {
        if (containers.find { it.configuration == config }) {
            return getExistingContainer(config)
        } else if (containers.size() + stoppingContainers.size() < maxContainers) {
            return createContainer(config)
        } else if (!unusedContainers.isEmpty()) {
            stopAndRemoveRandomUnusedContainer()
            return createOrGetExistingContainer(config)
        } else {
            throw new IllegalStateException("Should never happen")
        }
    }

    private void stopAndRemoveRandomUnusedContainer() {
        println("Entered stopper")
        def container = unusedContainers.min { it.creationTime }
        containers.remove(container)
        stoppingContainers.add(container)
        registryLock.unlock()

        container.stop()
        registryLock.lock()
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

    private <T> T guardWithLock(Closure<T> closure) {
        try {
            registryLock.lock()
            closure()
        } finally {
            registryLock.unlock()
        }
    }

    @EqualsAndHashCode(includes = ["uuid"])
    static class ContainerWrapper {

        UUID uuid = UUID.randomUUID()
        Map<String, String> configuration
        Instant creationTime

        ContainerWrapper(Map<String, String> configuration) {
            this.configuration = configuration
            this.creationTime = Instant.now()
        }

        void start() {
            println("starting $uuid container")
        }

        void stop() {
            println("stopping $uuid container")
        }
    }
}
