import dev.lukebemish.javacpostprocessor.PostProcessor;
import dev.lukebemish.larder.processor.ORMPostProcessor;

module dev.lukebemish.larder.processor {
    requires dev.lukebemish.javacpostprocessor;
    requires org.objectweb.asm;
    requires java.compiler;
    requires jdk.compiler;
    requires org.jspecify;
    requires com.google.auto.service;

    provides PostProcessor with ORMPostProcessor;
}
