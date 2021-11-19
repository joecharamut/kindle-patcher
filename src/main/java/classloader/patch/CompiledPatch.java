package classloader.patch;

import org.objectweb.asm.*;
import org.objectweb.asm.tree.*;
import org.objectweb.asm.util.CheckClassAdapter;
import org.objectweb.asm.util.TraceClassVisitor;
import rocks.spaghetti.classloader.Log;
import rocks.spaghetti.classloader.annotation.Overwrite;
import rocks.spaghetti.classloader.annotation.Patch;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.HashMap;
import java.util.Map;

public class CompiledPatch {
    private String targetClass = null;
    private Map<String, InsnList> methodOverwrites = new HashMap<>();

    public CompiledPatch(byte[] classFile) {
        ClassReader reader = new ClassReader(classFile);

        StringWriter sw = new StringWriter();
        PrintWriter pw = new PrintWriter(sw);
        reader.accept(new TraceClassVisitor(new PatchCompiler(null), pw), 0);

        Log.log(sw.toString());
        Log.log("target=" + targetClass);
    }

    public String getTarget() {
        return targetClass;
    }

    public byte[] apply(byte[] classBytes) {
        ClassReader reader = new ClassReader(classBytes);

        ClassNode classNode = new ClassNode();
        reader.accept(classNode, 0);

        applyPatch(classNode);

        ClassWriter writer = new ClassWriter(ClassWriter.COMPUTE_MAXS | ClassWriter.COMPUTE_FRAMES);
        StringWriter sw = new StringWriter();
        PrintWriter pw = new PrintWriter(sw);

        ClassVisitor visitor = writer;
        visitor = new CheckClassAdapter(visitor);
        visitor = new TraceClassVisitor(visitor, pw);

        try {
            classNode.accept(visitor);
            Log.log(sw.toString());
        } catch (Throwable t) {
            Log.log(t);
            return classBytes;
        }

        return writer.toByteArray();
    }

    private void applyPatch(ClassNode classNode) {
        for (MethodNode methodNode : classNode.methods) {
            String signature = methodNode.name + methodNode.desc;

            if (methodOverwrites.containsKey(signature)) {
                methodNode.instructions = methodOverwrites.get(signature);
            }
        }
    }

    private class PatchCompiler extends ClassVisitor {
        public PatchCompiler(ClassVisitor next) {
            super(Opcodes.ASM8, next);
        }

        @Override
        public AnnotationVisitor visitAnnotation(String descriptor, boolean visible) {
            String annotationClass = Util.descriptorToQualified(descriptor);

            if (annotationClass.equals(Patch.class.getCanonicalName())) {
                return new AnnotationVisitor(Opcodes.ASM8) {
                    @Override
                    public void visit(String name, Object value) {
                        if (name.equals("target")) {
                            if (!(value instanceof Type)) {
                                throw new PatchCompileException("Invalid @Patch target argument");
                            }
                            targetClass = ((Type) value).getClassName();
                        }
                    }
                };
            }

            return super.visitAnnotation(descriptor, visible);
        }

        @Override
        public MethodVisitor visitMethod(int access, String name, String descriptor, String signature, String[] exceptions) {
            MethodNode method = new MethodNode(Opcodes.ASM8);
            return new MethodVisitor(Opcodes.ASM8, method) {
                @Override
                public void visitEnd() {
                    if (method.visibleAnnotations != null) {
                        for (AnnotationNode node : method.visibleAnnotations) {
                            String annotationClass = Util.descriptorToQualified(node.desc);

                            if (annotationClass.equals(Overwrite.class.getCanonicalName())) {
                                if (!node.values.isEmpty()) {
                                    String key = (String) node.values.get(0);

                                    if (key.equals("method")) {
                                        methodOverwrites.put((String) node.values.get(1), method.instructions);
                                    }
                                }
                            }
                        }
                    }
                }
            };
        }
    }
}
