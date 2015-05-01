package pl.starchasers.furiouscraft;

import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassWriter;
import static org.objectweb.asm.Opcodes.*;
import org.objectweb.asm.Type;
import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.FieldInsnNode;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.MethodNode;

import java.util.Collections;
import java.util.Iterator;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Created by asie on 4/30/15.
 */
public class TransformerEntityTracker {
	private static final String trackedEntitiesSearchName = Transformer.OBFUSCATED?"c":"trackedEntities";
	private static final String trackedEntitiesName = Transformer.OBFUSCATED?"field_72793_b":"trackedEntities";

	public static Set getConcurrentHashSet(){
		return Collections.newSetFromMap(new ConcurrentHashMap());
	}

	public static byte[] transform(byte[] basicClass) {
		ClassNode cn = new ClassNode();
		new ClassReader(basicClass).accept(cn, 0);
		for (MethodNode m : cn.methods) {
			if (!"<init>".equals(m.name)) {
				continue;
			}
			AbstractInsnNode target = null;
			Iterator<AbstractInsnNode> iterator = m.instructions.iterator();
			while (iterator.hasNext() && target == null) {
				AbstractInsnNode ins = iterator.next();
				if (ins instanceof FieldInsnNode) {
					FieldInsnNode fins = (FieldInsnNode) ins;
					if (fins.name.equals(trackedEntitiesSearchName)) {
						target = fins;
						System.out.println("Patching EntityTracker!");
					}
				}
			}
			if (target != null) {
				m.instructions.remove(target.getPrevious().getPrevious().getPrevious());
				m.instructions.remove(target.getPrevious().getPrevious());
				m.instructions.remove(target.getPrevious());

				m.instructions.insertBefore(target, new MethodInsnNode(INVOKESTATIC, Type.getInternalName(TransformerEntityTracker.class), "getConcurrentHashSet", "()Ljava/util/Set;"));
				m.instructions.insertBefore(target, new FieldInsnNode(PUTFIELD, "net/minecraft/entity/EntityTracker", trackedEntitiesName, Type.getType(Set.class).getDescriptor()));
				m.instructions.remove(target);
			}
		}
		ClassWriter cw = new ClassWriter(ClassWriter.COMPUTE_MAXS);
		cn.accept(cw);

		return cw.toByteArray();
	}
}
