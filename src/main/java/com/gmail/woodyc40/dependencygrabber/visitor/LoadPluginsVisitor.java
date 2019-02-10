package com.gmail.woodyc40.dependencygrabber.visitor;

import org.objectweb.asm.Label;
import org.objectweb.asm.MethodVisitor;

import java.util.Set;

import static org.objectweb.asm.Opcodes.*;

public class LoadPluginsVisitor extends MethodVisitor {
    private final Set<String> finalLoadSet;

    private final Label loopCondition = new Label();
    private final Label loopBody = new Label();

    private boolean complete;

    public LoadPluginsVisitor(MethodVisitor methodVisitor, Set<String> finalLoadSet) {
        super(ASM7, methodVisitor);
        this.finalLoadSet = finalLoadSet;
    }

    @Override
    public void visitVarInsn(int opcode, int operand) {
        super.visitVarInsn(opcode, operand);

        if (opcode == ASTORE && operand == 6 && !this.complete) {
            this.complete = true;
            this.visitVarInsn(ALOAD, 6);
            this.visitMethodInsn(INVOKEINTERFACE, "org/bukkit/plugin/Plugin", "getName", "()Ljava/lang/String;", true);
            this.visitVarInsn(ASTORE, 8);

            for (String pluginName : finalLoadSet) {
                this.visitVarInsn(ALOAD, 8);
                this.visitLdcInsn(pluginName);
                this.visitMethodInsn(INVOKEVIRTUAL, "java/lang/String", "equals", "(Ljava/lang/Object;)Z", false);
                this.visitJumpInsn(IFNE, this.loopBody);
            }

            this.visitJumpInsn(GOTO, this.loopCondition);

            this.visitLabel(this.loopBody);
        }
    }

    @Override
    public void visitIincInsn(int var, int increment) {
        this.visitLabel(this.loopCondition);
        super.visitIincInsn(var, increment);
    }
}
