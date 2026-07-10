package me.micahcode.hqtiers.client;

import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.UUID;

import com.mojang.authlib.GameProfile;

import net.fabricmc.loader.api.FabricLoader;
import net.fabricmc.loader.api.MappingResolver;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.util.InputUtil;
import net.minecraft.text.Style;
import net.minecraft.util.Identifier;

public final class HqTiersMinecraftCompat {
	private HqTiersMinecraftCompat() {
	}

	public static UUID profileId(GameProfile profile) {
		try {
			return (UUID) GameProfile.class.getMethod("id").invoke(profile);
		} catch (ReflectiveOperationException ignored) {
			try {
				return (UUID) GameProfile.class.getMethod("getId").invoke(profile);
			} catch (ReflectiveOperationException exception) {
				throw new IllegalStateException("Could not read Minecraft profile UUID.", exception);
			}
		}
	}

	public static String profileName(GameProfile profile) {
		try {
			return (String) GameProfile.class.getMethod("name").invoke(profile);
		} catch (ReflectiveOperationException ignored) {
			try {
				return (String) GameProfile.class.getMethod("getName").invoke(profile);
			} catch (ReflectiveOperationException exception) {
				throw new IllegalStateException("Could not read Minecraft profile name.", exception);
			}
		}
	}

	public static Style fontStyle(Identifier fontId) {
		MappingResolver mappings = FabricLoader.getInstance().getMappingResolver();

		for (Method method : Style.class.getMethods()) {
			if (!method.getReturnType().isAssignableFrom(Style.class)) continue;
			if (method.getParameterCount() != 1) continue;

			Class<?> paramType = method.getParameterTypes()[0];

			// 1.21.2+
			if (paramType.isAssignableFrom(fontId.getClass())) {
				try {
					Object result = method.invoke(Style.EMPTY, fontId);
					if (result instanceof Style s) return s;
				} catch (Exception ignored) {}
			}

			try {
				Constructor<?> ctor = paramType.getConstructor(fontId.getClass());
				Object wrapped = ctor.newInstance(fontId);
				Object result = method.invoke(Style.EMPTY, wrapped);
				if (result instanceof Style s) return s;
			} catch (Exception ignored) {}

			for (Class<?> inner : paramType.getDeclaredClasses()) {
				try {
					Constructor<?> ctor = inner.getConstructor(fontId.getClass());
					Object wrapped = ctor.newInstance(fontId);
					Object result = method.invoke(Style.EMPTY, wrapped);
					if (result instanceof Style s) return s;
				} catch (Exception ignored) {}
			}
		}

		return Style.EMPTY;
	}

	public static KeyBinding keyBinding(String translationKey, int code, String categoryTranslationKey) {
		// 1.21.2+
		try {
			Constructor<KeyBinding> constructor = KeyBinding.class.getConstructor(
					String.class, InputUtil.Type.class, int.class, String.class);
			return constructor.newInstance(translationKey, InputUtil.Type.KEYSYM, code, categoryTranslationKey);
		} catch (ReflectiveOperationException ignored) {
		}

		// before 1.21.2
		try {
			return categorizedKeyBinding(translationKey, code,
					FabricLoader.getInstance().getMappingResolver());
		} catch (ReflectiveOperationException exception) {
			throw new IllegalStateException("Could not create HQTiers keybinding.", exception);
		}
	}

	private static Object cachedCategory = null;

	private static KeyBinding categorizedKeyBinding(String translationKey, int code, MappingResolver mappings) throws ReflectiveOperationException {
		if (cachedCategory == null) {
			try {
				Class<?> categoryClass = Class.forName(mappings.mapClassName("named", "net.minecraft.client.option.KeyBinding$Category"));
				String createName = mappings.mapMethodName(
						"named",
						"net.minecraft.client.option.KeyBinding$Category",
						"create",
						"(Lnet/minecraft/util/Identifier;)Lnet/minecraft/client/option/KeyBinding$Category;"
				);
				Method create = categoryClass.getMethod(createName, Identifier.class);
				create.setAccessible(true);
				cachedCategory = create.invoke(null, Identifier.of("assets", "category"));
			} catch (ReflectiveOperationException exception) {
				for (Class<?> categoryClass : KeyBinding.class.getDeclaredClasses()) {
					for (Method create : categoryClass.getDeclaredMethods()) {
						if (Modifier.isStatic(create.getModifiers())
								&& create.getParameterCount() == 1
								&& create.getParameterTypes()[0] == Identifier.class
								&& create.getReturnType() == categoryClass) {
							create.setAccessible(true);
							cachedCategory = create.invoke(null, Identifier.of("assets", "category"));
							break;
						}
					}
					if (cachedCategory != null) break;
				}
			}
			if (cachedCategory == null) {
				throw new ReflectiveOperationException("Could not create keybinding category.");
			}
		}

		Constructor<KeyBinding> constructor = KeyBinding.class.getConstructor(
				String.class, InputUtil.Type.class, int.class, cachedCategory.getClass());
		return constructor.newInstance(translationKey, InputUtil.Type.KEYSYM, code, cachedCategory);
	}
}
