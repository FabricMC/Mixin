package org.spongepowered.asm.mixin.transformer;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.objectweb.asm.ClassReader;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.MethodNode;

import org.spongepowered.asm.mixin.transformer.MixinConfig.IListener;
import org.spongepowered.asm.util.Bytecode;

public enum MixinInheritanceTracker implements IListener {
	INSTANCE;

	@Override
	public void onPrepare(MixinInfo mixin) {
	}

	@Override
	public void onInit(MixinInfo mixin) {
		ClassInfo mixinInfo = mixin.getClassInfo();
		assert mixinInfo.isMixin(); //The mixin should certainly be a mixin

		for (ClassInfo superType = mixinInfo.getSuperClass(); superType != null && superType.isMixin(); superType = superType.getSuperClass()) {
			parentMixins.computeIfAbsent(superType.getName(), k -> new ArrayList<MixinInfo>()).add(mixin);
		}
	}

	public List<MethodNode> findOverrides(ClassInfo owner, String name, String desc) {
		return findOverrides(owner.getName(), name, desc);
	}

	public List<MethodNode> findOverrides(String owner, String name, String desc) {
		List<MixinInfo> children = parentMixins.get(owner);
		if (children == null) return Collections.emptyList();

		List<MethodNode> out = new ArrayList<MethodNode>(children.size());

		for (MixinInfo child : children) {
			ClassNode node = child.getClassNode(ClassReader.SKIP_DEBUG | ClassReader.SKIP_FRAMES);

			MethodNode method = Bytecode.findMethod(node, name, desc);
			if (method == null || Bytecode.isStatic(method)) continue;

			switch (Bytecode.getVisibility(method)) {
			case PRIVATE:
				break;

			case PACKAGE:
				int ownerSplit = owner.lastIndexOf('/');
				int childSplit = child.getName().lastIndexOf('/');
				//There is a reasonable chance mixins are in the same package, so it is viable that a package private method is overridden
				if (ownerSplit != childSplit || (ownerSplit > 0 && !owner.regionMatches(0, child.getName(), 0, ownerSplit + 1))) break;

			default:
				out.add(method);
				break;
			}
		}

		return out.isEmpty() ? Collections.emptyList() : out;
	}

	private final Map<String, List<MixinInfo>> parentMixins = new HashMap<String, List<MixinInfo>>();
}