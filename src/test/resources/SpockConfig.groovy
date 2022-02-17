import org.spockframework.runtime.model.parallel.ExecutionMode

runner {
    parallel {
        enabled true
        defaultSpecificationExecutionMode ExecutionMode.SAME_THREAD
        defaultExecutionMode ExecutionMode.CONCURRENT
        def defaultThreadCount = 2
        def propertyThreadCount = System.getProperty("thread.count")
        def threadCount = propertyThreadCount ? propertyThreadCount as int : defaultThreadCount
        fixed(threadCount)
    }
}
