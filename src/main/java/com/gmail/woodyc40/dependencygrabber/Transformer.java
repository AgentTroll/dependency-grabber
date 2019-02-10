package com.gmail.woodyc40.dependencygrabber;

import com.gmail.woodyc40.dependencygrabber.visitor.EnablePluginsVisitor;
import com.gmail.woodyc40.dependencygrabber.visitor.LoadPluginsVisitor;
import lombok.RequiredArgsConstructor;
import org.bukkit.craftbukkit.v1_8_R3.CraftServer;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.MethodVisitor;

import java.io.IOException;
import java.lang.instrument.ClassFileTransformer;
import java.lang.instrument.IllegalClassFormatException;
import java.lang.instrument.Instrumentation;
import java.nio.file.Files;
import java.security.ProtectionDomain;
import java.util.Set;

import static org.objectweb.asm.Opcodes.ASM7;

@RequiredArgsConstructor
public class Transformer implements ClassFileTransformer {
    private static final Class<?> TARGET_CLASS = CraftServer.class;
    private static final String TARGET_CLASS_NAME = "org/bukkit/craftbukkit/v1_8_R3/CraftServer";

    private static final String LOAD_PLUGINS_NAME = "loadPlugins";
    private static final String LOAD_PLUGINS_DESC = "()V";

    private static final String ENABLE_PLUGINS_NAME = "enablePlugins";
    private static final String ENABLE_PLUGINS_DESC = "(Lorg/bukkit/plugin/PluginLoadOrder;)V";

    private final DependencyGrabber grabber;
    private final Set<String> finalLoadSet;
    private final Instrumentation instrumentation;

    public void doTransformation() {
        try {
            this.instrumentation.addTransformer(this, true);
            this.instrumentation.retransformClasses(TARGET_CLASS);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    @Override
    public byte[] transform(ClassLoader loader, String className, Class<?> classBeingRedefined, ProtectionDomain protectionDomain, byte[] classfileBuffer) throws IllegalClassFormatException {
        if (!className.equals(TARGET_CLASS_NAME)) {
            return classfileBuffer;
        }

        this.grabber.getLogger().info("Redefining " + TARGET_CLASS + "...");

        ClassReader reader = new ClassReader(classfileBuffer);
        ClassWriter writer = new ClassWriter(ClassWriter.COMPUTE_MAXS);
        reader.accept(new ClassVisitor(ASM7, writer) {
            @Override
            public MethodVisitor visitMethod(int access, String name, String desc, String signature, String[] exceptions) {
                MethodVisitor visitor = super.visitMethod(access, name, desc, signature, exceptions);
                if (LOAD_PLUGINS_NAME.equals(name) && LOAD_PLUGINS_DESC.equals(desc)) {
                    grabber.getLogger().info("Detected loadPlugins()");
                    return new LoadPluginsVisitor(visitor, finalLoadSet);
                }

                if (ENABLE_PLUGINS_NAME.equals(name) && ENABLE_PLUGINS_DESC.equals(desc)) {
                    grabber.getLogger().info("Detected enablePlugins()");
                    return new EnablePluginsVisitor(visitor, finalLoadSet);
                }

                return visitor;
            }
        }, 0);

        byte[] bytes = writer.toByteArray();
        try {
            Files.write(this.grabber.getDataFolder().toPath().resolve("output.class"), bytes);
        } catch (IOException e) {
            e.printStackTrace();
        }

        this.grabber.getLogger().info("Redefinition complete.");

        return bytes;
    }
}
