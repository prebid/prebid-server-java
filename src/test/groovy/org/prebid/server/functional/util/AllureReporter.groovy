package org.prebid.server.functional.util

import io.qameta.allure.Allure
import io.qameta.allure.AllureLifecycle
import io.qameta.allure.Description
import io.qameta.allure.Flaky
import io.qameta.allure.Muted
import io.qameta.allure.model.Label
import io.qameta.allure.model.Link
import io.qameta.allure.model.Parameter
import io.qameta.allure.model.Status
import io.qameta.allure.model.StatusDetails
import io.qameta.allure.model.TestResult
import io.qameta.allure.util.AnnotationUtils
import org.spockframework.runtime.AbstractRunListener
import org.spockframework.runtime.extension.IGlobalExtension
import org.spockframework.runtime.model.ErrorInfo
import org.spockframework.runtime.model.FeatureInfo
import org.spockframework.runtime.model.IterationInfo
import org.spockframework.runtime.model.MethodInfo
import org.spockframework.runtime.model.SpecInfo

import java.lang.annotation.Annotation
import java.lang.annotation.Repeatable
import java.lang.reflect.Method
import java.security.MessageDigest
import java.security.NoSuchAlgorithmException
import java.util.stream.Collectors
import java.util.stream.Stream

import static io.qameta.allure.util.ResultsUtils.createFrameworkLabel
import static io.qameta.allure.util.ResultsUtils.createHostLabel
import static io.qameta.allure.util.ResultsUtils.createLanguageLabel
import static io.qameta.allure.util.ResultsUtils.createPackageLabel
import static io.qameta.allure.util.ResultsUtils.createParameter
import static io.qameta.allure.util.ResultsUtils.createParentSuiteLabel
import static io.qameta.allure.util.ResultsUtils.createSubSuiteLabel
import static io.qameta.allure.util.ResultsUtils.createSuiteLabel
import static io.qameta.allure.util.ResultsUtils.createTestClassLabel
import static io.qameta.allure.util.ResultsUtils.createTestMethodLabel
import static io.qameta.allure.util.ResultsUtils.createThreadLabel
import static io.qameta.allure.util.ResultsUtils.firstNonEmpty
import static io.qameta.allure.util.ResultsUtils.getProvidedLabels
import static io.qameta.allure.util.ResultsUtils.getStatus
import static io.qameta.allure.util.ResultsUtils.getStatusDetails
import static java.nio.charset.StandardCharsets.UTF_8
import static java.util.Comparator.comparing

/**
 * This is a temporary port of https://github.com/allure-framework/allure-java/tree/master/allure-spock to add support
 * for Spock 2.0.
 * **/
class AllureReporter extends AbstractRunListener implements IGlobalExtension {

    private static final String FRAMEWORK = "spock"
    private static final String LANGUAGE = "groovy"
    private static final String MD5 = "md5"

    private final ThreadLocal<String> testUuid
            = InheritableThreadLocal.withInitial({ UUID.randomUUID().toString() })

    private final AllureLifecycle lifecycle

    AllureReporter() {
        this(Allure.getLifecycle())
    }

    AllureReporter(AllureLifecycle lifecycle) {
        this.lifecycle = lifecycle
    }

    @Override
    void visitSpec(SpecInfo spec) {
        spec.addListener(this)
    }

    @Override
    void beforeIteration(IterationInfo iteration) {
        String uuid = testUuid.get()
        FeatureInfo feature = iteration.feature
        SpecInfo spec = feature.spec
        List<Parameter> parameters = getParameters(iteration.dataVariables)
        SpecInfo subSpec = spec.subSpec
        SpecInfo superSpec = spec.superSpec
        String packageName = spec.package
        String specName = spec.name
        String testClassName = spec.reflection.name
        String testMethodName = iteration.name

        List<Label> labels = [createPackageLabel(packageName),
                              createTestClassLabel(testClassName),
                              createTestMethodLabel(testMethodName),
                              createSuiteLabel(specName),
                              createHostLabel(),
                              createThreadLabel(),
                              createFrameworkLabel(FRAMEWORK),
                              createLanguageLabel(LANGUAGE)]
        if (Objects.nonNull(subSpec)) {
            labels.add(createSubSuiteLabel(subSpec.name))
        }
        if (Objects.nonNull(superSpec)) {
            labels.add(createParentSuiteLabel(superSpec.name))
        }
        labels.addAll(getLabels(iteration))
        labels.addAll(getProvidedLabels())

        TestResult result = new TestResult()
                .setUuid(uuid)
                .setHistoryId(getHistoryId(getQualifiedName(iteration), parameters))
                .setName(firstNonEmpty(feature.displayName, testMethodName, getQualifiedName(iteration))
                        .orElse("Unknown"))
                .setFullName(getQualifiedName(iteration))
                .setStatusDetails(new StatusDetails()
                        .setFlaky(isFlaky(feature))
                        .setMuted(isMuted(feature)))
                .setParameters(parameters)
                .setLinks(getLinks(iteration))
                .setLabels(labels)
        processDescription(feature, result)
        lifecycle.scheduleTestCase(result)
        lifecycle.startTestCase(uuid)
    }

    private static List<Label> getLabels(IterationInfo iterationInfo) {
        Set<Label> labels = AnnotationUtils.getLabels(iterationInfo.feature.featureMethod.annotations)
        labels.addAll(AnnotationUtils.getLabels(iterationInfo.feature.spec.annotations))
        return new ArrayList<>(labels)
    }

    private static void processDescription(FeatureInfo feature, TestResult item) {
        List<Description> annotationsOnFeature = getFeatureAnnotations(feature, Description)
        if (!annotationsOnFeature.isEmpty()) {
            item.setDescription(annotationsOnFeature[0].value())
        }
    }

    private static String getQualifiedName(IterationInfo iteration) {
        return "${iteration.feature.spec.reflection.name}.${iteration.name}"
    }

    private static String getHistoryId(String name, List<Parameter> parameters) {
        MessageDigest digest = getMessageDigest()
        digest.update(name.getBytes(UTF_8))
        parameters.stream()
                  .sorted(comparing(Parameter::getName).thenComparing(Parameter::getValue as Comparator))
                  .forEachOrdered(parameter -> {
                      digest.update(parameter.name.getBytes(UTF_8))
                      digest.update(parameter.value.getBytes(UTF_8))
                  })
        byte[] bytes = digest.digest()
        return new BigInteger(1, bytes).toString(16)
    }

    private static MessageDigest getMessageDigest() {
        try {
            return MessageDigest.getInstance(MD5)
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("Could not find md5 hashing algorithm", e)
        }
    }

    private static boolean isFlaky(FeatureInfo iteration) {
        return hasAnnotation(iteration, Flaky)
    }

    private static boolean isMuted(FeatureInfo iteration) {
        return hasAnnotation(iteration, Muted)
    }

    private static boolean hasAnnotation(FeatureInfo feature, Class<? extends Annotation> clazz) {
        return hasAnnotationOnFeature(feature, clazz) || hasAnnotationOnSpec(feature.spec, clazz)
    }

    private static boolean hasAnnotationOnSpec(SpecInfo spec, Class<? extends Annotation> clazz) {
        return !getSpecAnnotations(spec, clazz).isEmpty()
    }

    private static boolean hasAnnotationOnFeature(FeatureInfo feature, Class<? extends Annotation> clazz) {
        return !getFeatureAnnotations(feature, clazz).isEmpty()
    }

    private static List<Link> getLinks(IterationInfo iteration) {
        List<Link> links = new ArrayList<>()
        links.addAll(AnnotationUtils.getLinks(iteration.feature.featureMethod.annotations))
        links.addAll(AnnotationUtils.getLinks(iteration.feature.spec.annotations))
        return links
    }

    private static <T extends Annotation> List<T> getAnnotationsOnMethod(MethodInfo methodInfo, Class<T> clazz) {
        T annotation = methodInfo.getAnnotation(clazz)
        return Stream.concat(extractRepeatableAnnotations(methodInfo, clazz).stream(),
                Objects.isNull(annotation) ? Stream.empty() : Stream.of(annotation)).collect(Collectors.toList())
    }

    private static <T extends Annotation> List<T> extractRepeatableAnnotations(MethodInfo result, Class<T> clazz) {
        if (clazz.isAnnotationPresent(Repeatable)) {
            Repeatable repeatable = clazz.getAnnotation(Repeatable)
            Class<? extends Annotation> wrapper = repeatable.value()
            Annotation annotation = result.getAnnotation(wrapper)
            if (Objects.nonNull(annotation)) {
                try {
                    Method value = annotation.class.getMethod("value")
                    Object annotations = value.invoke(annotation)
                    return Arrays.asList((T[]) annotations)
                } catch (Exception e) {
                    throw new IllegalStateException(e)
                }
            }
        }
        return Collections.emptyList()
    }

    private static <T extends Annotation> List<T> getFeatureAnnotations(FeatureInfo feature,
                                                                        Class<T> clazz) {
        return getAnnotationsOnMethod(feature.featureMethod, clazz)
    }

    private static <T extends Annotation> List<T> getSpecAnnotations(SpecInfo spec,
                                                                     Class<T> clazz) {
        return getAnnotationsOnClass(spec.reflection, clazz)
    }

    private static <T extends Annotation> List<T> getAnnotationsOnClass(Class<?> result, Class<T> clazz) {
        return Arrays.asList(result.getAnnotationsByType(clazz))
    }


    private static List<Parameter> getParameters(Map<String, ?> dataVariables) {
        return dataVariables.collect { createParameter(it.key, it.value) }

    }

    @Override
    void error(ErrorInfo error) {
        String uuid = testUuid.get()
        Throwable exception = error.exception
        lifecycle.updateTestCase(uuid, {
            it.setStatus(getStatus(exception).orElse(null))
              .setStatusDetails(getStatusDetails(exception).orElse(null))
        })
    }

    @Override
    void afterIteration(IterationInfo iteration) {
        String uuid = testUuid.get()
        testUuid.remove()

        lifecycle.updateTestCase(uuid, {
            if (Objects.isNull(it.status)) {
                it.setStatus(Status.PASSED)
            }
        })
        lifecycle.stopTestCase(uuid)
        lifecycle.writeTestCase(uuid)
    }
}
