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
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import net.minecraft.core.Holder;
import net.minecraft.util.CubicSpline;
import net.minecraft.world.level.levelgen.DensityFunction;
import net.minecraft.world.level.levelgen.DensityFunctions;
import net.minecraft.world.level.levelgen.NoiseRouter;
import org.apache.commons.io.IOUtils;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.Label;
import org.objectweb.asm.Type;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.FieldNode;
import org.objectweb.asm.tree.MethodNode;
import org.objectweb.asm.util.CheckClassAdapter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class DensityFunctionCompiler {
    private static class TooSimpleException extends RuntimeException {}

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

    private static final ConcurrentHashMap<DensityFunction, DensityFunction> compilationCache =
            new ConcurrentHashMap<>();

    static final AtomicLong reusedCalls = new AtomicLong();
    static final AtomicLong totalCalls = new AtomicLong();

    private static boolean shouldKeepFunctionType(DensityFunction df) {
        return df instanceof CompiledDensityFunction
                || df instanceof DensityFunctions.BeardifierOrMarker
                || df instanceof DensityFunctions.BlendAlpha
                || df instanceof DensityFunctions.BlendOffset;
    }

    public static NoiseRouter compileNoiseRouter(NoiseRouter raw) {
        return new NoiseRouter(
                DensityFunctionCompiler.compile(raw.barrierNoise()),
                DensityFunctionCompiler.compile(raw.fluidLevelFloodednessNoise()),
                DensityFunctionCompiler.compile(raw.fluidLevelSpreadNoise()),
                DensityFunctionCompiler.compile(raw.lavaNoise()),
                DensityFunctionCompiler.compile(raw.temperature()),
                DensityFunctionCompiler.compile(raw.vegetation()),
                DensityFunctionCompiler.compile(raw.continents()),
                DensityFunctionCompiler.compile(raw.erosion()),
                DensityFunctionCompiler.compile(raw.depth()),
                DensityFunctionCompiler.compile(raw.ridges()),
                DensityFunctionCompiler.compile(raw.initialDensityWithoutJaggedness()),
                DensityFunctionCompiler.compile(raw.finalDensity()),
                DensityFunctionCompiler.compile(raw.veinToggle()),
                DensityFunctionCompiler.compile(raw.veinRidged()),
                DensityFunctionCompiler.compile(raw.veinGap()));
    }

    public static DensityFunction compile(DensityFunction df) {
        if (shouldKeepFunctionType(df)) {
            return df;
        }
        boolean reused = true;
        DensityFunction compiled = compilationCache.get(df);
        if (compiled == null) {
            reused = false;
            // not found, compile and put
            // don't use computeIfAbsent because we may recursively enter this function again from the compiler
            compiled = compileFresh(df);
            compilationCache.put(df, compiled);
        }
        long total = totalCalls.incrementAndGet();
        long tReused;
        if (reused) {
            tReused = reusedCalls.incrementAndGet();
        } else {
            tReused = reusedCalls.get();
        }
        // TODO: Make toggleable in settings
        if ((total % 100) == 0) {
            logger.info("DF Compiler cache hits {} {}/{}", (double) tReused / total, tReused, total);
        }

        return compiled;
    }

    private static CubicSpline<DensityFunctions.Spline.Point, DensityFunctions.Spline.Coordinate> compileSpline(
            CubicSpline<DensityFunctions.Spline.Point, DensityFunctions.Spline.Coordinate> spline) {
        if (spline
                instanceof
                CubicSpline.Multipoint<DensityFunctions.Spline.Point, DensityFunctions.Spline.Coordinate>
                mp) {
            final DensityFunction rawCoord = mp.coordinate().function().value();
            final DensityFunction compiledCoord = compile(rawCoord);
            final List<CubicSpline<DensityFunctions.Spline.Point, DensityFunctions.Spline.Coordinate>> values =
                    mp.values().stream()
                            .map(DensityFunctionCompiler::compileSpline)
                            .toList();
            return new CubicSpline.Multipoint<>(
                    new DensityFunctions.Spline.Coordinate(Holder.direct(compiledCoord)),
                    mp.locations(),
                    values,
                    mp.derivatives(),
                    mp.minValue(),
                    mp.maxValue());
        } else {
            return spline;
        }
    }

    @SuppressWarnings("unchecked")
    private static DensityFunction compileFresh(DensityFunction df) {
        if (df instanceof DensityFunctions.Marker marker) {
            final DensityFunction compiled = compile(marker.wrapped());
            return switch (marker.type()) {
                case Interpolated -> DensityFunctions.interpolated(compiled);
                case FlatCache -> DensityFunctions.flatCache(compiled);
                case Cache2D -> DensityFunctions.cache2d(compiled);
                case CacheOnce -> DensityFunctions.cacheOnce(compiled);
                case CacheAllInCell -> DensityFunctions.cacheAllInCell(compiled);
            };
        } else if (df instanceof DensityFunctions.Spline splineFn) {
            final CubicSpline<DensityFunctions.Spline.Point, DensityFunctions.Spline.Coordinate> rawSpline =
                    splineFn.spline();
            final CubicSpline<DensityFunctions.Spline.Point, DensityFunctions.Spline.Coordinate> compiledSpline =
                    compileSpline(rawSpline);
            if (rawSpline != compiledSpline) {
                return DensityFunctions.spline(compiledSpline);
            } else {
                return df;
            }
        } else if (shouldKeepFunctionType(df)) {
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
        if (k.fields == null) {
            k.fields = new ArrayList<>();
        }

        // index
        {
            MethodNode m = k.methods.stream()
                    .filter(mn -> mn.name.equals("functionIndex"))
                    .findFirst()
                    .orElseThrow();
            m.instructions.clear();
            m.visitCode();
            m.visitLdcInsn(classIndex);
            m.visitInsn(LRETURN);
            m.visitMaxs(0, 0);
            m.visitEnd();
        }
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
            try {
                populateCompute(df, k, m, storedDfs, storedNoises);
            } catch (TooSimpleException e) {
                return df;
            }
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
        if (ctx.comprisedOps < 4) {
            throw new TooSimpleException();
        }
        ctx.finish();
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
        private final MethodNode ctor;
        private final List<DensityFunction> storedDfs;
        private final List<DensityFunction.NoiseHolder> storedNoises;
        private int currentVar;
        public int comprisedOps = 0;

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
            this.ctor = kls.methods.stream()
                    .filter(mn -> mn.name.equals("<init>"))
                    .findFirst()
                    .orElseThrow();

            while (this.ctor.instructions.getLast().getOpcode() != RETURN) {
                this.ctor.instructions.remove(this.ctor.instructions.getLast());
            }
            this.ctor.instructions.remove(this.ctor.instructions.getLast());
        }

        private String addStoredDensityFunction(DensityFunction df) {
            for (int i = 0; i < storedDfs.size(); i++) {
                if (storedDfs.get(i) == df) {
                    return "storedDf" + i;
                }
            }
            final int idx = storedDfs.size();
            storedDfs.add(df);
            final String fieldName = "storedDf" + idx;

            kls.fields.add(new FieldNode(
                    ACC_PUBLIC | ACC_FINAL, fieldName, Type.getDescriptor(DensityFunction.class), null, null));

            ctor.visitVarInsn(ALOAD, 0);
            ctor.visitVarInsn(ALOAD, 2); // load functions[]
            ctor.visitLdcInsn(idx);
            ctor.visitInsn(AALOAD);
            ctor.visitFieldInsn(PUTFIELD, kls.name, fieldName, Type.getDescriptor(DensityFunction.class));

            return fieldName;
        }

        private String addStoredNoise(DensityFunction.NoiseHolder nh) {
            for (int i = 0; i < storedNoises.size(); i++) {
                if (storedNoises.get(i) == nh) {
                    return "storedNoise" + i;
                }
            }
            final int idx = storedNoises.size();
            storedNoises.add(nh);
            final String fieldName = "storedNoise" + idx;

            kls.fields.add(new FieldNode(
                    ACC_PUBLIC | ACC_FINAL,
                    fieldName,
                    Type.getDescriptor(DensityFunction.NoiseHolder.class),
                    null,
                    null));

            ctor.visitVarInsn(ALOAD, 0);
            ctor.visitVarInsn(ALOAD, 3); // load noises[]
            ctor.visitLdcInsn(idx);
            ctor.visitInsn(AALOAD);
            ctor.visitFieldInsn(PUTFIELD, kls.name, fieldName, Type.getDescriptor(DensityFunction.NoiseHolder.class));

            return fieldName;
        }

        public void finish() {
            ctor.visitInsn(RETURN);
            ctor.visitMaxs(0, 0);
            ctor.visitEnd();
        }

        /**
         * Generates code that pushes the value of df.compute(arg0) onto the Java stack
         * @param gdf The density function to recursively translate
         */
        public void visitCompute(DensityFunction gdf) {
            comprisedOps++;
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
            } else if (gdf instanceof DensityFunctions.BlendDensity df) {
                visitCompute(df.input());
                m.visitVarInsn(ALOAD, 1);
                m.visitMethodInsn(
                        INVOKESTATIC,
                        tUtils.getInternalName(),
                        "blendDensity",
                        tBlendDensityMethod.getDescriptor(),
                        false);
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
            } else if (gdf instanceof DensityFunctions.Noise df) {
                final int vX = currentVar;
                final int vY = currentVar + 2;
                final int vZ = currentVar + 4;
                currentVar += 6;

                m.visitVarInsn(ALOAD, 1); // context
                m.visitMethodInsn(
                        INVOKESTATIC,
                        tUtils.getInternalName(),
                        "getFctxXAsDouble",
                        tGetFctxCoordAsDoubleMethod.getDescriptor(),
                        false);
                m.visitLdcInsn(df.xzScale());
                m.visitInsn(DMUL);
                m.visitVarInsn(DSTORE, vX);

                m.visitVarInsn(ALOAD, 1); // context
                m.visitMethodInsn(
                        INVOKESTATIC,
                        tUtils.getInternalName(),
                        "getFctxYAsDouble",
                        tGetFctxCoordAsDoubleMethod.getDescriptor(),
                        false);
                m.visitLdcInsn(df.yScale());
                m.visitInsn(DMUL);
                m.visitVarInsn(DSTORE, vY);

                m.visitVarInsn(ALOAD, 1); // context
                m.visitMethodInsn(
                        INVOKESTATIC,
                        tUtils.getInternalName(),
                        "getFctxZAsDouble",
                        tGetFctxCoordAsDoubleMethod.getDescriptor(),
                        false);
                m.visitLdcInsn(df.xzScale());
                m.visitInsn(DMUL);
                m.visitVarInsn(DSTORE, vZ);

                final String storedField = addStoredNoise(df.noise());
                m.visitVarInsn(ALOAD, 0);
                m.visitFieldInsn(
                        GETFIELD, kls.name, storedField, Type.getDescriptor(DensityFunction.NoiseHolder.class));
                m.visitVarInsn(DLOAD, vX);
                m.visitVarInsn(DLOAD, vY);
                m.visitVarInsn(DLOAD, vZ);
                m.visitMethodInsn(
                        INVOKESTATIC,
                        tUtils.getInternalName(),
                        "getNoiseValue",
                        tGetNoiseValueMethod.getDescriptor(),
                        false);
            } else if (gdf instanceof DensityFunctions.ShiftedNoise df) {
                final int vX = currentVar;
                final int vY = currentVar + 2;
                final int vZ = currentVar + 4;
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

                final String storedField = addStoredNoise(df.noise());
                m.visitVarInsn(ALOAD, 0);
                m.visitFieldInsn(
                        GETFIELD, kls.name, storedField, Type.getDescriptor(DensityFunction.NoiseHolder.class));
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
                if (gdf instanceof DensityFunctions.Marker || gdf instanceof DensityFunctions.Spline) {
                    gdf = compile(gdf);
                }
                // Fallback to calling a stored object, these functions are really complex
                final String storedField = addStoredDensityFunction(gdf);
                m.visitVarInsn(ALOAD, 0);
                m.visitFieldInsn(GETFIELD, kls.name, storedField, Type.getDescriptor(DensityFunction.class));
                m.visitVarInsn(ALOAD, 1);
                m.visitMethodInsn(
                        INVOKESTATIC, tUtils.getInternalName(), "compute", tComputeMethod.getDescriptor(), false);
            }
        }
    }
}
