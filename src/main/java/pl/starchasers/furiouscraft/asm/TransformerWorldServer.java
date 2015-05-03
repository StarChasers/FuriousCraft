package pl.starchasers.furiouscraft.asm;

import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.MethodNode;
import org.objectweb.asm.tree.TypeInsnNode;

import java.util.Iterator;

public class TransformerWorldServer {
	private static final String createChunkProviderName = Transformer.OBFUSCATED ? "j" : "createChunkProvider";

	public static byte[] transform(byte[] basicClass) {
		ClassNode cn = new ClassNode();
		new ClassReader(basicClass).accept(cn, 0);

		for (MethodNode m : cn.methods) {
			if (!createChunkProviderName.equals(m.name)) {
				continue;
			}
			AbstractInsnNode target = null;
			Iterator<AbstractInsnNode> iterator = m.instructions.iterator();
			while (iterator.hasNext() && target == null) {
				AbstractInsnNode ins = iterator.next();
				if (ins instanceof TypeInsnNode) {
					TypeInsnNode tins = (TypeInsnNode) ins;
					if ("net/minecraft/world/gen/ChunkProviderServer".equals(tins.desc) || "ms".equals(tins.desc)) {
						tins.desc = "pl/starchasers/furiouscraft/hooks/ChunkProviderServerFurious";
						System.out.println("ChunkProvider patched (1/2)!");
					}
				} else if (ins instanceof MethodInsnNode) {
					MethodInsnNode mins = (MethodInsnNode) ins;
					if ("net/minecraft/world/gen/ChunkProviderServer".equals(mins.owner) || "ms".equals(mins.owner)) {
						mins.owner = "pl/starchasers/furiouscraft/hooks/ChunkProviderServerFurious";
						System.out.println("ChunkProvider patched (2/2)!");
					}
				}
			}
		}
		ClassWriter cw = new ClassWriter(ClassWriter.COMPUTE_MAXS);
		cn.accept(cw);

		return cw.toByteArray();
	}
}
