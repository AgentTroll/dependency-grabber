package com.gmail.woodyc40.dependencygrabber;

import lombok.RequiredArgsConstructor;
import org.bukkit.craftbukkit.v1_8_R3.CraftServer;
import org.bukkit.plugin.SimplePluginManager;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.MethodVisitor;

import java.io.InputStream;
import java.lang.instrument.ClassDefinition;
import java.lang.instrument.Instrumentation;
import java.nio.file.Files;
import java.util.Set;

import static org.objectweb.asm.Opcodes.*;

@RequiredArgsConstructor
public class Transformer {
    private static final String LOAD_PLUGINS_NAME =
            "loadPlugins";
    private static final String LOAD_PLUGINS_DESC =
            "()V";

    private final DependencyGrabber plugin;
    private final Set<String> finalLoadSet;
    private final Instrumentation instrumentation;

    public void doTransformation() {
        try (InputStream is = getClassBytes(CraftServer.class)) {
            ClassReader reader = new ClassReader(is);
            ClassWriter writer = new ClassWriter(ClassWriter.COMPUTE_MAXS);
            reader.accept(new ClassVisitor(ASM7, writer) {
                @Override
                public MethodVisitor visitMethod(int access, String name, String desc, String signature, String[] exceptions) {
                    MethodVisitor visitor = super.visitMethod(access, name, desc, signature, exceptions);
                    if (!LOAD_PLUGINS_NAME.equals(name) || !LOAD_PLUGINS_DESC.equals(desc)) {
                        return visitor;
                    }

                    return new MethodVisitor(ASM7, visitor) {
                        private boolean complete;

                        @Override
                        public void visitVarInsn(int opcode, int operand) {
                            super.visitVarInsn(opcode, operand);

                            if (opcode == ASTORE && operand == 6 && !this.complete) {
                                this.complete = true;
                                this.visitVarInsn(ALOAD, 6);
                                this.visitMethodInsn(INVOKEINTERFACE, "org/bukkit/plugin/Plugin", "getName", "()Ljava/lang/StringZ", true);
                                this.visitVarInsn(ASTORE, 8);

                                for (String pluginName : finalLoadSet) {
                                    this.visitVarInsn(ALOAD, 8);
                                    this.visitLdcInsn(pluginName);
                                    this.visitMethodInsn(INVOKEVIRTUAL, "java/lang/String", "equals", "(Ljava/lang/Object;)Z", false);
                                    this.visitIntInsn(IFNE, 61);
                                }

                                // Goto statement is in an unreliable index so manually incr counter
                                this.visitIincInsn(5, 1);
                                this.visitIntInsn(GOTO, 59);
                            }
                        }
                    };
                }
            }, 0);

            byte[] bytes = writer.toByteArray();
            Files.write(this.plugin.getDataFolder().toPath().resolve("output.class"), bytes);
            this.instrumentation.redefineClasses(new ClassDefinition(SimplePluginManager.class, bytes));
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private static InputStream getClassBytes(Class<?> target) {
        return target.getResourceAsStream(target.getSimpleName() + ".class");
    }
}
