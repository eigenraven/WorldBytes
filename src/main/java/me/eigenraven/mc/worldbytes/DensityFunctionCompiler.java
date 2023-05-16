package me.eigenraven.mc.worldbytes;

import java.io.IOException;
import java.lang.invoke.MethodHandles;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicLong;
import net.minecraft.world.level.levelgen.DensityFunction;
import org.apache.commons.io.IOUtils;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.tree.ClassNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class DensityFunctionCompiler {
    private DensityFunctionCompiler() {}

    private static final Logger logger = LoggerFactory.getLogger("worldbytes-DFC");
    public static final AtomicLong classCounter = new AtomicLong(1);
    private static final byte[] templateClassBytes;

    static {
        final Class<CompiledDensityFunctionTemplate> kTemplateClass = CompiledDensityFunctionTemplate.class;
        final String classPath = kTemplateClass.getName().replace('.', '/') + ".class";
        try {
            templateClassBytes = IOUtils.toByteArray(
                    Objects.requireNonNull(kTemplateClass.getClassLoader().getResourceAsStream(classPath)));
        } catch (Exception e) {
            logger.error("Could not load the class bytes for {}", classPath, e);
            throw new RuntimeException(e);
        }
    }

    @SuppressWarnings("unchecked")
    public static DensityFunction compile(DensityFunction df) {
        if (df instanceof CompiledDensityFunction) {
            return df;
        }
        final long classIndex = classCounter.incrementAndGet();

        final ClassNode k = new ClassNode();
        {
            final ClassReader templateCopier = new ClassReader(templateClassBytes);
            templateCopier.accept(k, ClassReader.SKIP_FRAMES);
        }

        k.name += "$" + classIndex;

        final ClassWriter kWriter = new ClassWriter(ClassWriter.COMPUTE_MAXS | ClassWriter.COMPUTE_FRAMES);
        k.accept(kWriter);
        final byte[] kBytes = kWriter.toByteArray();

        final Class<? extends CompiledDensityFunction> klass;
        try {
            klass = (Class<? extends CompiledDensityFunction>)
                    MethodHandles.lookup().defineClass(kBytes);
        } catch (Throwable e) {
            final String errorFilePath = "CompiledDensityFunction$" + classIndex + ".class";
            logger.error("Could not load generated class bytes: ", e);
            final Path errorPath = FileSystems.getDefault().getPath(errorFilePath);
            logger.error("Attempting to save failed class to {}", errorPath.toAbsolutePath());
            try {
                Files.write(errorPath, kBytes);
            } catch (IOException ex) {
                logger.error("Could not save failed class", ex);
            }
            throw new RuntimeException(e);
        }
        logger.debug("Compiled and loaded {}", klass.getName());

        final CompiledDensityFunction instance;
        try {
            instance = klass.getConstructor(DensityFunction.class).newInstance(df);
        } catch (ReflectiveOperationException e) {
            throw new RuntimeException(e);
        }

        return instance;
    }
}
