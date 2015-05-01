package pl.starchasers.furiouscraft;

import java.io.IOException;
import java.util.Collections;
import java.util.Iterator;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;
import org.objectweb.asm.tree.*;


import net.minecraft.launchwrapper.IClassTransformer;
import net.minecraft.launchwrapper.LaunchClassLoader;

public class Transformer implements IClassTransformer, Opcodes {
	public static final boolean OBFUSCATED;

    static {
        boolean obf = true;
        try {
            obf = ((LaunchClassLoader) Transformer.class.getClassLoader()).getClassBytes("net.minecraft.world.World") == null;
        } catch (IOException iox) {
        }
        OBFUSCATED = obf;
    }

	@Override
	public byte[] transform(String name, String transformedName, byte[] basicClass) {
		if ("net.minecraft.entity.EntityTracker".equals(transformedName)) {
			return TransformerEntityTracker.transform(basicClass);
		} else if ("net.minecraft.world.WorldServer".equals(transformedName)) {
			return TransformerWorldServer.transform(basicClass);
		}
		return basicClass;
	}

}
