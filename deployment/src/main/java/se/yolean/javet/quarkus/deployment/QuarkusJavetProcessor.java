package se.yolean.javet.quarkus.deployment;

import java.io.File;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.logging.Logger;

import javax.inject.Inject;

import com.caoccao.javet.enums.JSRuntimeType;
import com.caoccao.javet.exceptions.JavetException;
import com.caoccao.javet.interop.loader.JavetLibLoader;
import com.caoccao.javet.utils.JavetOSUtils;

import io.quarkus.deployment.annotations.BuildProducer;
import io.quarkus.deployment.annotations.BuildStep;
import io.quarkus.deployment.annotations.ExecutionTime;
import io.quarkus.deployment.annotations.Record;
import io.quarkus.deployment.builditem.FeatureBuildItem;
import io.quarkus.deployment.builditem.LaunchModeBuildItem;
import io.quarkus.deployment.builditem.nativeimage.JniRuntimeAccessBuildItem;
import io.quarkus.deployment.builditem.nativeimage.NativeImageResourceBuildItem;
import io.quarkus.deployment.builditem.nativeimage.ReflectiveClassBuildItem;
import io.quarkus.deployment.builditem.nativeimage.RuntimeReinitializedClassBuildItem;
import io.quarkus.deployment.pkg.NativeConfig;
import se.yolean.javet.quarkus.runtime.QuarkusJavetRecorder;

class QuarkusJavetProcessor {

  private static final String FEATURE = "javet";

  private static final Logger LOGGER = Logger.getLogger(QuarkusJavetProcessor.class.getSimpleName());

  @BuildStep
  @Record(ExecutionTime.RUNTIME_INIT)
  void registerLibraryRecorder(QuarkusJavetRecorder recorder) {
    recorder.loadLibrary();
  }

  @BuildStep
  void build(BuildProducer<FeatureBuildItem> feature,
      BuildProducer<ReflectiveClassBuildItem> reflectiveClasses,
      BuildProducer<JniRuntimeAccessBuildItem> jniRuntimeAccessibleClasses,
      BuildProducer<RuntimeReinitializedClassBuildItem> reinitialized,
      BuildProducer<NativeImageResourceBuildItem> nativeLibs,
      LaunchModeBuildItem launchMode,
      NativeConfig config) {

    feature.produce(new FeatureBuildItem(FEATURE));

    // String libFileName = extractLibToClasspathFile();
    //String libFileName = "libjavet-v8-linux-x86_64.v.1.0.2.so";
    // This is the resourceFileName in JavetLibLoader
    String libFileName = "/libjavet-v8-linux-x86_64.v.1.0.2.so";

    QuarkusJavetRecorder.checkClassPathResource(libFileName);
    extractLibToClasspathFile();
    QuarkusJavetRecorder.checkClassPathResource(libFileName);

    // https://github.com/quarkusio/quarkus/blob/2.4.0.Final/core/test-extension/deployment/src/main/java/io/quarkus/extest/deployment/TestProcessor.java#L126
    // https://github.com/quarkusio/quarkus/tree/2.4.0.Final/core/test-extension/deployment/src/main/resources

    registerClassesThatAreLoadedThroughReflection(reflectiveClasses, launchMode);
    registerClassesThatAreAccessedViaJni(jniRuntimeAccessibleClasses);
    addSupportForJavetMode(nativeLibs, config, libFileName);
    enableLoadOfNativeLibs(reinitialized);
  }

  private void registerClassesThatAreLoadedThroughReflection(BuildProducer<ReflectiveClassBuildItem> reflectiveClasses,
      LaunchModeBuildItem launchMode) {
    // https://github.com/quarkusio/quarkus/blob/2.4.0.Final/extensions/kafka-streams/deployment/src/main/java/io/quarkus/kafka/streams/deployment/KafkaStreamsProcessor.java#L60
    registerCompulsoryClasses(reflectiveClasses);
    registerClassesThatClientMaySpecify(reflectiveClasses, launchMode);
  }

  private void registerCompulsoryClasses(BuildProducer<ReflectiveClassBuildItem> reflectiveClasses) {
    // TODO any such classes in Javet?
  }

  private void registerClassesThatClientMaySpecify(BuildProducer<ReflectiveClassBuildItem> reflectiveClasses,
        LaunchModeBuildItem launchMode) {
    // TODO any such classes in Javet?
  }

  private void registerClassesThatAreAccessedViaJni(BuildProducer<JniRuntimeAccessBuildItem> jniRuntimeAccessibleClasses) {
    // TODO any such classes in Javet?
  }

  private void addSupportForJavetMode(BuildProducer<NativeImageResourceBuildItem> nativeLibs, NativeConfig nativeConfig,
      String modeSpecificLibFileName) {
    if (nativeConfig.isContainerBuild()) {
      nativeLibs.produce(new NativeImageResourceBuildItem(modeSpecificLibFileName));
      LOGGER.info("Added native image resource build item: " + modeSpecificLibFileName);
    }
    // otherwise the native lib of the platform this build runs on
    else {
      // https://github.com/quarkusio/quarkus/blob/2.4.0.Final/extensions/kafka-streams/deployment/src/main/java/io/quarkus/kafka/streams/deployment/KafkaStreamsProcessor.java#L130
      LOGGER.warning("Javet lib loading not implemented for !isContainerBuild");
      nativeLibs.produce(new NativeImageResourceBuildItem(modeSpecificLibFileName));
      LOGGER.info("Added native image resource build item: " + modeSpecificLibFileName);
      //throw new UnsupportedOperationException("Only targeting container build for now");
    }
  }

  private void enableLoadOfNativeLibs(BuildProducer<RuntimeReinitializedClassBuildItem> reinitialized) {
    // https://github.com/quarkusio/quarkus/blob/2.4.0.Final/extensions/kafka-streams/deployment/src/main/java/io/quarkus/kafka/streams/deployment/KafkaStreamsProcessor.java#L135
    reinitialized.produce(new RuntimeReinitializedClassBuildItem(JavetLibLoader.class.getName()));
  }

  private String extractLibToClasspathFile() {
    File buildTimeFileClasspath = new File("target/classes/");
    if (!buildTimeFileClasspath.exists()) {
      throw new RuntimeException("target/classes/ required for native build, at " + (new File(".")).getAbsolutePath());
    }

    // V8 only for now
    JavetLibLoader javetLibLoader = new JavetLibLoader(JSRuntimeType.V8);

    // Call private method
    Method deployLibFile;
    try {
      deployLibFile = JavetLibLoader.class.getDeclaredMethod("deployLibFile", String.class, java.io.File.class);
    } catch (NoSuchMethodException | SecurityException e) {
      throw new RuntimeException("TODO handle error", e);
    }
    deployLibFile.setAccessible(true);

    // https://github.com/caoccao/Javet/blob/1.0.2/src/main/java/com/caoccao/javet/interop/loader/JavetLibLoader.java#L325
    if (!JavetOSUtils.IS_LINUX) {
      throw new RuntimeException("Javet native build should run in Linux; quarkus-javet currently only targets linux containers");
    }
    String resourceFileName;
    try {
      resourceFileName = javetLibLoader.getResourceFileName();
    } catch (JavetException e) {
      throw new RuntimeException("TODO handle error", e);
    }
    LOGGER.info("Javet resourceFileName=" + resourceFileName);

    String libFileName;
    try {
      libFileName = javetLibLoader.getLibFileName();
    } catch (JavetException e) {
      throw new RuntimeException("TODO handle error", e);
    }
    LOGGER.info("Javet libFileName=" + libFileName);

    if (!resourceFileName.equals("/" + libFileName)) {
      throw new RuntimeException("Unexpected resourceFileName " + resourceFileName + " in relation to libFileName " + libFileName);
    }

    File libFile = new File(buildTimeFileClasspath, libFileName).getAbsoluteFile();

    // https://github.com/caoccao/Javet/blob/1.0.2/src/main/java/com/caoccao/javet/interop/loader/JavetLibLoader.java#L339
    try {
      deployLibFile.invoke(javetLibLoader, resourceFileName, libFile);
    } catch (IllegalAccessException | IllegalArgumentException | InvocationTargetException e) {
      if (e.getCause() instanceof JavetException) {
        throw new RuntimeException("TODO handle error", e.getCause());
      }
      throw new RuntimeException("Failed to access private JavetLibLoader method. Rethink.");
    }

    File expectExtractedFile = new File(buildTimeFileClasspath, libFileName);
    if (!expectExtractedFile.exists()) {
      throw new RuntimeException("Failed to verify that the lib file was extracted at " + expectExtractedFile);
    }
    return libFileName;
  }

}
