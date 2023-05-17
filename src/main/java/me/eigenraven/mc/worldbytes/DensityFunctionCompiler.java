package me.eigenraven.mc.worldbytes;

import static org.objectweb.asm.Opcodes.*;

import java.io.IOException;
import java.io.PrintWriter;
import java.lang.invoke.MethodHandles;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicLong;
import net.minecraft.world.level.levelgen.DensityFunction;
import net.minecraft.world.level.levelgen.DensityFunctions;
import org.apache.commons.io.IOUtils;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.Label;
import org.objectweb.asm.Type;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.MethodNode;
import org.objectweb.asm.util.CheckClassAdapter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class DensityFunctionCompiler {
    private DensityFunctionCompiler() {}

    private static final Logger logger = LoggerFactory.getLogger("worldbytes-DFC");
    public static final AtomicLong classCounter = new AtomicLong(1);
    private static final byte[] templateClassBytes;

    private static final Type tUtils = Type.getType(DensityFunctionUtils.class);
    private static final Type tFunctionContext = Type.getType(DensityFunction.FunctionContext.class);
    private static final Type tBlendDensityMethod =
            Type.getMethodType(Type.DOUBLE_TYPE, Type.DOUBLE_TYPE, tFunctionContext);
    private static final boolean debugWrite = Boolean.getBoolean("worldbytes.debug.writeClasses");

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
        if (df instanceof CompiledDensityFunction
                || df instanceof DensityFunctions.BeardifierMarker
                || df instanceof DensityFunctions.BlendAlpha
                || df instanceof DensityFunctions.BlendOffset) {
            return df;
        }
        final long classIndex = classCounter.incrementAndGet();
        final String errorFilePath = "CompiledDensityFunction$" + classIndex + ".class";

        final ClassNode k = new ClassNode();
        {
            final ClassReader templateCopier = new ClassReader(templateClassBytes);
            templateCopier.accept(k, ClassReader.SKIP_FRAMES);
        }

        k.name += "$" + classIndex;

        // min value
        {
            MethodNode m = k.methods.stream()
                    .filter(mn -> mn.name.equals("compiledMinValue"))
                    .findFirst()
                    .orElseThrow();
            m.instructions.clear();
            m.visitCode();
            m.visitLdcInsn(df.minValue());
            m.visitInsn(DRETURN);
            m.visitMaxs(0, 0);
            m.visitEnd();
        }
        // max value
        {
            MethodNode m = k.methods.stream()
                    .filter(mn -> mn.name.equals("compiledMaxValue"))
                    .findFirst()
                    .orElseThrow();
            m.instructions.clear();
            m.visitCode();
            m.visitLdcInsn(df.maxValue());
            m.visitInsn(DRETURN);
            m.visitMaxs(0, 0);
            m.visitEnd();
        }
        // compute
        {
            MethodNode m = k.methods.stream()
                    .filter(mn -> mn.name.equals("compiledCompute"))
                    .findFirst()
                    .orElseThrow();
            m.instructions.clear();
            populateCompute(df, m);
        }

        final ClassWriter kWriter = new ClassWriter(ClassWriter.COMPUTE_MAXS | ClassWriter.COMPUTE_FRAMES);
        k.accept(kWriter);
        final byte[] kBytes = kWriter.toByteArray();

        if (debugWrite) {
            dumpClass(errorFilePath, kBytes);
        }

        final Class<? extends CompiledDensityFunction> klass;
        try {
            klass = (Class<? extends CompiledDensityFunction>)
                    MethodHandles.lookup().defineClass(kBytes);
        } catch (Throwable e) {
            logger.error("Could not load generated class bytes: ", e);
            dumpClass(errorFilePath, kBytes);
            if (e instanceof VerifyError) {
                CheckClassAdapter.verify(
                        new ClassReader(kBytes),
                        DensityFunctionCompiler.class.getClassLoader(),
                        true,
                        new PrintWriter(System.err));
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

    private static void populateCompute(DensityFunction df, MethodNode m) {
        m.visitCode();
        final Context ctx = new Context(m, 2);
        ctx.visitCompute(df);
        m.visitInsn(DRETURN);
        m.visitMaxs(0, 0);
        m.visitEnd();
    }

    private static void dumpClass(String fileName, byte[] kBytes) {
        final Path filePath = FileSystems.getDefault().getPath(fileName);
        logger.error("Attempting to save class to {}", filePath.toAbsolutePath());
        try {
            Files.write(filePath, kBytes);
        } catch (IOException ex) {
            logger.error("Could not save failed class", ex);
        }
    }

    private static class Context {
        private final MethodNode m;
        private int currentVar;

        Context(MethodNode m, int currentVar) {
            this.m = m;
            this.currentVar = currentVar;
        }

        /**
         * Generates code that pushes the value of df.compute(arg0) onto the Java stack
         * @param gdf The density function to recursively translate
         */
        public void visitCompute(DensityFunction gdf) {
            if (gdf instanceof DensityFunctions.Constant df) {
                m.visitLdcInsn(df.value());
            } else if (gdf instanceof DensityFunctions.MulOrAdd df) {
                visitCompute(df.input());
                final double argConst = df.argument();
                m.visitLdcInsn(argConst);
                switch (df.type()) {
                    case ADD -> {
                        m.visitInsn(DADD);
                    }
                    case MUL -> {
                        m.visitInsn(DMUL);
                    }
                    default -> throw new IllegalStateException(df.type().getSerializedName());
                }
            } else if (gdf instanceof DensityFunctions.Ap2 df) {
                visitCompute(df.argument1());
                switch (df.type()) {
                    case ADD -> {
                        visitCompute(df.argument2());
                        m.visitInsn(DADD);
                    }
                    case MUL -> {
                        final int a1 = currentVar;
                        currentVar += 2;
                        m.visitInsn(DUP2);
                        m.visitVarInsn(DSTORE, a1);

                        // if (a1 == 0) { 0 } else { a1 * a2 }
                        m.visitInsn(DCONST_0);
                        m.visitInsn(DCMPL);
                        final Label ifNotZero = new Label();
                        final Label endFn = new Label();
                        m.visitJumpInsn(IFNE, ifNotZero);
                        // equal 0
                        m.visitInsn(DCONST_0);
                        m.visitVarInsn(DSTORE, a1); // overload a1 with the output value
                        m.visitJumpInsn(GOTO, endFn);
                        // not equal 0
                        m.visitLabel(ifNotZero);
                        visitCompute(df.argument2());

                        m.visitVarInsn(DLOAD, a1);
                        m.visitInsn(DUP2_X2);
                        m.visitInsn(POP2); // (a2, a1) -> (a1, a2)
                        m.visitInsn(DMUL); // a1 * a2
                        m.visitVarInsn(DSTORE, a1); // overload a1 with the output value
                        m.visitLabel(endFn);
                        m.visitVarInsn(DLOAD, a1);
                    }
                    case MIN, MAX -> {
                        final boolean isMin = df.type() == DensityFunctions.TwoArgumentSimpleFunction.Type.MIN;
                        final double boundary = isMin
                                ? df.argument2().minValue()
                                : df.argument2().maxValue();
                        final int a1 = currentVar;
                        currentVar += 2;
                        m.visitInsn(DUP2);
                        m.visitVarInsn(DSTORE, a1);

                        // if (a1 <> boundary) { a1 } else { Math.minmax(a1, a2) }
                        m.visitLdcInsn(boundary);
                        m.visitInsn(DCMPL);
                        final Label endFn = new Label();
                        m.visitJumpInsn(isMin ? IFLT : IFGT, endFn);
                        // beyond boundary - short circuit (goto endFn)
                        // not beyond boundary; compute a2 and the min/max
                        visitCompute(df.argument2());

                        m.visitVarInsn(DLOAD, a1);
                        m.visitInsn(DUP2_X2);
                        m.visitInsn(POP2); // (a2, a1) -> (a1, a2)
                        m.visitMethodInsn(
                                INVOKESTATIC, Type.getInternalName(Math.class), isMin ? "min" : "max", "(DD)D", false);
                        m.visitVarInsn(DSTORE, a1); // overload a1 with the output value
                        m.visitLabel(endFn);
                        m.visitVarInsn(DLOAD, a1);
                    }
                    default -> throw new IllegalStateException(df.type().getSerializedName());
                }
            } else if (gdf instanceof DensityFunctions.BeardifierMarker) {
                m.visitInsn(DCONST_0);
            } else if (gdf instanceof DensityFunctions.BlendAlpha) {
                m.visitInsn(DCONST_1);
            } else if (gdf instanceof DensityFunctions.BlendDensity df) {
                visitCompute(df.input());
                m.visitVarInsn(ALOAD, 1);
                m.visitMethodInsn(
                        INVOKESTATIC,
                        tUtils.getInternalName(),
                        "blendDensity",
                        tBlendDensityMethod.getDescriptor(),
                        false);
            } else if (gdf instanceof DensityFunctions.BlendOffset) {
                m.visitInsn(DCONST_0);
            } else {
                throw new UnsupportedOperationException(
                        "Unknown density function type " + gdf.getClass() + " : " + gdf.codec());
            }
        }
    }
}
