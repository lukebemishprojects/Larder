package dev.lukebemish.larder.processor;

import com.google.auto.service.AutoService;
import dev.lukebemish.javacpostprocessor.PostProcessor;
import org.jspecify.annotations.Nullable;
import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.FieldVisitor;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.RecordComponentVisitor;
import org.objectweb.asm.Type;

import javax.lang.model.element.TypeElement;
import javax.lang.model.util.Elements;
import javax.lang.model.util.Types;
import javax.tools.JavaFileManager;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Objects;
import java.util.function.Consumer;

@AutoService(PostProcessor.class)
public class ORMPostProcessor implements PostProcessor {
    @Override
    public String name() {
        return "dev.lukebemish.larder.processor.orm";
    }

    private Context.@Nullable BinaryBridge binaryBridge;
    private Types types;
    private Elements elements;

    @Override
    public void context(Context context) {
        this.binaryBridge = context.binaryBridge();
        this.types = context.task().getTypes();
        this.elements = context.task().getElements();
    }

    @Override
    public ClassVisitor visit(ClassVisitor next, String binaryName, JavaFileManager fileManager, JavaFileManager.Location location) {
        var binaryBridge = Objects.requireNonNull(this.binaryBridge);
        var types = Objects.requireNonNull(this.types);
        var elements = Objects.requireNonNull(this.elements);
        if (binaryName.startsWith("dev.lukebemish.larder.schema.")) {
            // We need to process this class
            // If it's a model, add the annotation
            // If it's `Schema`, add the list of models
            return new ClassVisitor(Opcodes.ASM9, next) {
                private @Nullable Consumer<ClassVisitor> annotationWork;
                private boolean doRepresentationsField;

                @Override
                public void visit(int version, int access, String name, String signature, String superName, String[] interfaces) {
                    super.visit(version, access, name, signature, superName, interfaces);

                    var modelType = binaryBridge.typeByDescriptor("Ldev/lukebemish/larder/orm/Model;");
                    var superTypes = new ArrayList<>(Arrays.asList(interfaces));
                    superTypes.add(superName);
                    for (var type : superTypes) {
                        var mirror = binaryBridge.typeByDescriptor("L"+type+";");
                        if (types.isAssignable(mirror, modelType)) {
                            annotationWork = cv -> {
                                var av = cv.visitAnnotation(
                                    "Ldev/lukebemish/larder/orm/World$BelongsTo",
                                    true
                                );
                                av.visit("value", Type.getObjectType("dev/lukebemish/larder/schema/LarderWorld"));
                                av.visitEnd();
                            };
                            break;
                        }
                    }

                    if (name.equals("dev/lukebemish/larder/schema/LarderWorld")) {
                        doRepresentationsField = true;
                    }
                }

                @Override
                public void visitNestMember(String nestMember) {
                    doPendingAnnotation();
                    super.visitNestMember(nestMember);
                }

                @Override
                public void visitPermittedSubclass(String permittedSubclass) {
                    doPendingAnnotation();
                    super.visitPermittedSubclass(permittedSubclass);
                }

                @Override
                public void visitInnerClass(String name, String outerName, String innerName, int access) {
                    doPendingAnnotation();
                    super.visitInnerClass(name, outerName, innerName, access);
                }

                @Override
                public RecordComponentVisitor visitRecordComponent(String name, String descriptor, String signature) {
                    doPendingAnnotation();
                    return super.visitRecordComponent(name, descriptor, signature);
                }

                @Override
                public FieldVisitor visitField(int access, String name, String descriptor, String signature, Object value) {
                    doPendingAnnotation();
                    return super.visitField(access, name, descriptor, signature, value);
                }

                @Override
                public MethodVisitor visitMethod(int access, String name, String descriptor, String signature, String[] exceptions) {
                    doPendingAnnotation();
                    if (doRepresentationsField && name.equals("<clinit>")) {
                        return new MethodVisitor(Opcodes.ASM9, super.visitMethod(access, name, descriptor, signature, exceptions)) {
                            @Override
                            public void visitTypeInsn(int opcode, String type) {
                                if (opcode == Opcodes.ANEWARRAY && type.equals("dev/lukebemish/larder/orm/Representation")) {
                                    visitInsn(Opcodes.POP); // drop the array size
                                    var representationClasses = new ArrayList<Type>();
                                    var modelType = binaryBridge.typeByDescriptor("Ldev/lukebemish/larder/orm/Model;");
                                    for (var element : elements.getPackageElement("dev.lukebemish.larder.schema").getEnclosedElements()) {
                                        if (element instanceof TypeElement typeElement) {
                                            var mirror = element.asType();
                                            if (types.isAssignable(mirror, modelType)) {
                                                representationClasses.add(
                                                    Type.getObjectType(elements.getBinaryName(typeElement).toString().replace('.', '/'))
                                                );
                                            }
                                        }
                                    }
                                    visitLdcInsn(representationClasses.size());
                                    super.visitTypeInsn(opcode, type);
                                    for (int i = 0; i < representationClasses.size(); i++) {
                                        var t = representationClasses.get(i);
                                        visitInsn(Opcodes.DUP);
                                        visitLdcInsn(i);
                                        visitLdcInsn(t);
                                        visitMethodInsn(
                                            Opcodes.INVOKESTATIC,
                                            "dev/lukebemish/larder/orm/Representation",
                                            "expensiveLocate",
                                            Type.getMethodDescriptor(
                                                Type.getObjectType("dev/lukebemish/larder/orm/Representation"),
                                                Type.getType(Class.class)
                                            ),
                                            false
                                        );
                                        visitInsn(Opcodes.AASTORE);
                                    }
                                } else {
                                    super.visitTypeInsn(opcode, type);
                                }
                            }
                        };
                    } else {
                        return super.visitMethod(access, name, descriptor, signature, exceptions);
                    }
                }

                @Override
                public void visitEnd() {
                    doPendingAnnotation();
                    super.visitEnd();
                }

                private void doPendingAnnotation() {
                    if (annotationWork != null) {
                        annotationWork.accept(this);
                        annotationWork = null;
                    }
                }
            };
        }
        return next;
    }
}
