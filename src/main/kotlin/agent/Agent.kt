package agent

import jdk.internal.org.objectweb.asm.ClassReader
import jdk.internal.org.objectweb.asm.ClassVisitor
import jdk.internal.org.objectweb.asm.ClassWriter
import jdk.internal.org.objectweb.asm.MethodVisitor
import jdk.internal.org.objectweb.asm.Opcodes
import jdk.internal.org.objectweb.asm.Type
import jdk.internal.org.objectweb.asm.commons.GeneratorAdapter
import jdk.internal.org.objectweb.asm.commons.Method
import java.io.PrintStream
import java.lang.instrument.ClassFileTransformer
import java.lang.instrument.Instrumentation
import java.security.ProtectionDomain

class Agent {
    companion object {
        @JvmStatic
        fun premain(agentArgs: String?, inst: Instrumentation) {
            println("Agent started.")
            inst.addTransformer(TestInterceptorClassFileTransformer())
        }
    }
}

class TestInterceptorClassFileTransformer : ClassFileTransformer {
    override fun transform(loader: ClassLoader?, className: String?, classBeingRedefined: Class<*>?,
                           protectionDomain: ProtectionDomain?, classfileBuffer: ByteArray?): ByteArray? {
        val cr = ClassReader(classfileBuffer)
        val cw = ClassWriter(ClassWriter.COMPUTE_FRAMES or ClassWriter.COMPUTE_MAXS)
        val ca = ClassTestInterceptor(cw)
        cr.accept(ca, ClassReader.EXPAND_FRAMES)
        return cw.toByteArray()
    }
}

class ClassTestInterceptor(visitor: ClassVisitor) : ClassVisitor(Opcodes.ASM5, visitor) {
    override fun visitMethod(access: Int, name: String?, desc: String?, signature: String?,
                             exceptions: Array<out String>?): MethodVisitor {
        var v = super.visitMethod(access, name, desc, signature, exceptions)
        v = TestInterceptor(v, access, name, desc)
        return v
    }
}

class TestInterceptor(delegate: MethodVisitor, access: Int, name: String?, desc: String?) :
        GeneratorAdapter(Opcodes.ASM5, delegate, access, name, desc) {

    override fun visitMethodInsn(opcode: Int, owner: String?, name: String?, desc: String?, itf: Boolean) {
        if (opcode == Opcodes.INVOKESTATIC
                && owner == "example/CoroutineExampleKt"
                && name == "test"
                && desc == "(Lkotlin/coroutines/experimental/Continuation;)Ljava/lang/Object;"
                && !itf) {
            val printlnM = PrintStream::class.java.getMethod("println", String::class.java)
            getStatic(Type.getType(System::class.java), "out", Type.getType(PrintStream::class.java))
            visitLdcInsn("Test detected")
            invokeVirtual(Type.getType(PrintStream::class.java), Method.getMethod(printlnM))
        }

        super.visitMethodInsn(opcode, owner, name, desc, itf)
    }
}
