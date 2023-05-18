package me.eigenraven.mc.worldbytes;

import static org.objectweb.asm.Opcodes.*;

import java.io.IOException;
import java.io.PrintWriter;
import java.lang.invoke.MethodHandles;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
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
    private static final Type tDF = Type.getType(DensityFunction.class);
    private static final Type tDFArr = Type.getType(DensityFunction[].class);
    private static final Type tCDF = Type.getType(CompiledDensityFunction.class);
    private static final Type tNoiseHolder = Type.getType(DensityFunction.NoiseHolder.class);
    private static final Type tNoiseHolderArr = Type.getType(DensityFunction.NoiseHolder[].class);
    private static final Type tFunctionContext = Type.getType(DensityFunction.FunctionContext.class);
    private static final Type tComputeMethod = Type.getMethodType(Type.DOUBLE_TYPE, tDF, tFunctionContext);
    private static final Type tGetFctxCoordAsDoubleMethod = Type.getMethodType(Type.DOUBLE_TYPE, tFunctionContext);
    private static final Type tGetNoiseValueMethod =
            Type.getMethodType(Type.DOUBLE_TYPE, tNoiseHolder, Type.DOUBLE_TYPE, Type.DOUBLE_TYPE, Type.DOUBLE_TYPE);
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
        if (df instanceof DensityFunctions.Marker marker) {
            final DensityFunction compiled = compile(marker.wrapped());
            return switch (marker.type()) {
                case Interpolated -> DensityFunctions.interpolated(compiled);
                case FlatCache -> DensityFunctions.flatCache(compiled);
                case Cache2D -> DensityFunctions.cache2d(compiled);
                case CacheOnce -> DensityFunctions.cacheOnce(compiled);
                case CacheAllInCell -> DensityFunctions.cacheAllInCell(compiled);
            };
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
        final List<DensityFunction> storedDfs = new ArrayList<>();
        final List<DensityFunction.NoiseHolder> storedNoises = new ArrayList<>();
        // compute
        {
            MethodNode m = k.methods.stream()
                    .filter(mn -> mn.name.equals("compiledCompute"))
                    .findFirst()
                    .orElseThrow();
            m.instructions.clear();
            populateCompute(df, k, m, storedDfs, storedNoises);
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
            instance = klass.getConstructor(
                            DensityFunction.class, DensityFunction[].class, DensityFunction.NoiseHolder[].class)
                    .newInstance(
                            df,
                            storedDfs.toArray(new DensityFunction[0]),
                            storedNoises.toArray(new DensityFunction.NoiseHolder[0]));
        } catch (ReflectiveOperationException e) {
            throw new RuntimeException(e);
        }

        return instance;
    }

    private static void populateCompute(
            DensityFunction df,
            ClassNode kls,
            MethodNode m,
            List<DensityFunction> storedDfs,
            List<DensityFunction.NoiseHolder> storedNoises) {
        m.visitCode();
        final Context ctx = new Context(kls, m, storedDfs, storedNoises, 2);
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
        private final ClassNode kls;
        private final MethodNode m;
        private final List<DensityFunction> storedDfs;
        private final List<DensityFunction.NoiseHolder> storedNoises;
        private int currentVar;

        Context(
                ClassNode kls,
                MethodNode m,
                List<DensityFunction> storedDfs,
                List<DensityFunction.NoiseHolder> storedNoises,
                int currentVar) {
            this.kls = kls;
            this.m = m;
            this.storedDfs = storedDfs;
            this.storedNoises = storedNoises;
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
            } else if (gdf instanceof DensityFunctions.Clamp df) {
                visitCompute(df.input());
                m.visitLdcInsn(df.minValue());
                m.visitLdcInsn(df.maxValue());
                m.visitMethodInsn(INVOKESTATIC, tUtils.getInternalName(), "clamp", "(DDD)D", false);
            } else if (gdf instanceof DensityFunctions.HolderHolder df) {
                visitCompute(df.function().value());
            } else if (gdf instanceof DensityFunctions.Mapped df) {
                visitCompute(df.input());
                switch (df.type()) {
                    case ABS -> {
                        m.visitMethodInsn(INVOKESTATIC, Type.getInternalName(Math.class), "abs", "(D)D", false);
                    }
                    case SQUARE -> {
                        m.visitInsn(DUP2);
                        m.visitInsn(DMUL);
                    }
                    case CUBE -> {
                        m.visitInsn(DUP2);
                        m.visitInsn(DUP2);
                        m.visitInsn(DMUL);
                        m.visitInsn(DMUL);
                    }
                    case HALF_NEGATIVE, QUARTER_NEGATIVE -> {
                        m.visitInsn(DUP2);
                        m.visitInsn(DCONST_0);
                        m.visitInsn(DCMPG);
                        final Label gtZero = new Label();
                        final Label ifEnd = new Label();
                        m.visitJumpInsn(IFGT, gtZero);
                        // If <= 0
                        m.visitLdcInsn(
                                switch (df.type()) {
                                    case HALF_NEGATIVE -> 0.5D;
                                    case QUARTER_NEGATIVE -> 0.25D;
                                    default -> throw new IllegalStateException();
                                });
                        m.visitInsn(DMUL);
                        // If > 0, no-op
                        m.visitLabel(gtZero);
                    }
                    case SQUEEZE -> {
                        m.visitLdcInsn(-1.0);
                        m.visitLdcInsn(1.0);
                        m.visitMethodInsn(INVOKESTATIC, tUtils.getInternalName(), "clamp", "(DDD)D", false);
                        // e
                        m.visitLdcInsn(2.0D);
                        m.visitInsn(DDIV); // e / 2.0
                        m.visitInsn(DUP2); // (e/2, e/2)
                        m.visitInsn(DUP2);
                        m.visitInsn(DUP2);
                        m.visitInsn(DMUL);
                        m.visitInsn(DMUL); // (e/2, e*e*e/8)
                        m.visitLdcInsn(3.0);
                        m.visitInsn(DDIV); // (e/2, e*e*e/24)
                        m.visitInsn(DSUB); // e/2 - e*e*e/24
                    }
                }
            } else if (gdf instanceof DensityFunctions.Marker df) {
                visitCompute(df.wrapped());
            } else if (gdf instanceof DensityFunctions.RangeChoice df) {
                visitCompute(df.input());
                m.visitInsn(DUP2);

                final Label outOfRange = new Label(), endIf = new Label();
                m.visitLdcInsn(df.minInclusive());
                m.visitInsn(DCMPG);
                m.visitJumpInsn(IFLT, outOfRange); // if d<this.minInclusive
                m.visitInsn(DUP2); // keep a consistent stack size of the oor branch
                m.visitLdcInsn(df.maxExclusive());
                m.visitInsn(DCMPL);
                m.visitJumpInsn(IFGE, outOfRange);
                m.visitInsn(POP2);
                // in range
                visitCompute(df.whenInRange());
                m.visitJumpInsn(GOTO, endIf);

                m.visitLabel(outOfRange);
                m.visitInsn(POP2); // remove the duplicated input value
                visitCompute(df.whenOutOfRange());

                m.visitLabel(endIf);
            } else if (gdf instanceof DensityFunctions.ShiftedNoise df) {
                final int vX = currentVar;
                final int vY = currentVar + 2;
                final int vZ = currentVar + 4;
                final int noiseIdx = storedNoises.size();
                storedNoises.add(df.noise());
                currentVar += 6;

                visitCompute(df.shiftX());
                m.visitVarInsn(ALOAD, 1); // context
                m.visitMethodInsn(
                        INVOKESTATIC,
                        tUtils.getInternalName(),
                        "getFctxXAsDouble",
                        tGetFctxCoordAsDoubleMethod.getDescriptor(),
                        false);
                m.visitLdcInsn(df.xzScale());
                m.visitInsn(DMUL);
                m.visitInsn(DADD);
                m.visitVarInsn(DSTORE, vX);

                visitCompute(df.shiftY());
                m.visitVarInsn(ALOAD, 1); // context
                m.visitMethodInsn(
                        INVOKESTATIC,
                        tUtils.getInternalName(),
                        "getFctxYAsDouble",
                        tGetFctxCoordAsDoubleMethod.getDescriptor(),
                        false);
                m.visitLdcInsn(df.yScale());
                m.visitInsn(DMUL);
                m.visitInsn(DADD);
                m.visitVarInsn(DSTORE, vY);

                visitCompute(df.shiftZ());
                m.visitVarInsn(ALOAD, 1); // context
                m.visitMethodInsn(
                        INVOKESTATIC,
                        tUtils.getInternalName(),
                        "getFctxZAsDouble",
                        tGetFctxCoordAsDoubleMethod.getDescriptor(),
                        false);
                m.visitLdcInsn(df.xzScale());
                m.visitInsn(DMUL);
                m.visitInsn(DADD);
                m.visitVarInsn(DSTORE, vZ);

                m.visitVarInsn(ALOAD, 0);
                m.visitFieldInsn(GETFIELD, tCDF.getInternalName(), "noises", tNoiseHolderArr.getDescriptor());
                m.visitLdcInsn(noiseIdx);
                m.visitInsn(AALOAD);
                m.visitVarInsn(DLOAD, vX);
                m.visitVarInsn(DLOAD, vY);
                m.visitVarInsn(DLOAD, vZ);
                m.visitMethodInsn(
                        INVOKESTATIC,
                        tUtils.getInternalName(),
                        "getNoiseValue",
                        tGetNoiseValueMethod.getDescriptor(),
                        false);
            } else if (gdf instanceof DensityFunctions.YClampedGradient df) {
                m.visitVarInsn(ALOAD, 1); // context
                m.visitMethodInsn(
                        INVOKESTATIC,
                        tUtils.getInternalName(),
                        "getFctxYAsDouble",
                        tGetFctxCoordAsDoubleMethod.getDescriptor(),
                        false);
                m.visitLdcInsn((double) df.fromY());
                m.visitLdcInsn((double) df.toY());
                m.visitLdcInsn(df.fromValue());
                m.visitLdcInsn(df.toValue());
                m.visitMethodInsn(INVOKESTATIC, tUtils.getInternalName(), "clampedMap", "(DDDDD)D", false);
            } else {
                // Fallback to calling a stored object, these functions are really complex
                final int index = storedDfs.size();
                storedDfs.add(gdf);
                m.visitVarInsn(ALOAD, 0);
                m.visitFieldInsn(GETFIELD, tCDF.getInternalName(), "functions", tDFArr.getDescriptor());
                m.visitLdcInsn(index);
                m.visitInsn(AALOAD);
                m.visitVarInsn(ALOAD, 1);
                m.visitMethodInsn(
                        INVOKESTATIC, tUtils.getInternalName(), "compute", tComputeMethod.getDescriptor(), false);
                if (!(gdf instanceof DensityFunctions.EndIslandDensityFunction
                        || gdf instanceof DensityFunctions.Noise
                        || gdf instanceof DensityFunctions.ShiftNoise // Covers Shift, ShiftA and ShiftB
                        || gdf instanceof DensityFunctions.Spline
                        || gdf instanceof DensityFunctions.WeirdScaledSampler
                        || gdf instanceof DensityFunctions.Spline)) {

                    // TODO: Make this non-fatal once all vanilla functions are implemented
                    throw new UnsupportedOperationException(
                            "Unknown density function type " + gdf.getClass() + " : " + gdf.codec());
                }
            }
        }
    }
}
