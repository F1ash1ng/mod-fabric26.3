package com.example.mobilitydevice.client;

import com.example.mobilitydevice.MobilityDeviceMod;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.model.geom.ModelPart;
import java.lang.reflect.Method;
import java.util.Map;
import java.util.WeakHashMap;

/** Per-player wall-run fallback to the ordinary animated skin overlay. */
public final class SkinLayersWallRunCompatibility {
    private static final boolean LOADED = FabricLoader.getInstance().isModLoaded("skinlayers3d");
    private static final Map<ModelPart, SavedMesh> SUPPRESSED = new WeakHashMap<>();
    private static Method getMesh, getOffset, setMesh;
    private static boolean unavailable;
    private static final class SavedMesh { Object mesh, offset; }
    private SkinLayersWallRunCompatibility() {}
    public static boolean isLoaded() { return LOADED && !unavailable; }

    private static boolean ready() {
        if (!isLoaded()) return false;
        try {
            if (getMesh == null) {
                Class<?> injector = Class.forName("dev.tr7zw.skinlayers.accessor.ModelPartInjector");
                Class<?> mesh = Class.forName("dev.tr7zw.skinlayers.api.Mesh");
                Class<?> offset = Class.forName("dev.tr7zw.skinlayers.api.OffsetProvider");
                getMesh = injector.getMethod("getInjectedMesh");
                getOffset = injector.getMethod("getOffsetProvider");
                setMesh = injector.getMethod("setInjectedMesh", mesh, offset);
            }
            return true;
        } catch (ReflectiveOperationException | LinkageError | RuntimeException e) {
            report(e); return false;
        }
    }

    /** Mark all six overlays; disabling voxels never hides the ordinary overlay geometry. */
    public static Runnable suppress(ModelPart part) {
        if (!ready() || SUPPRESSED.containsKey(part)) return () -> {};
        SavedMesh saved = new SavedMesh();
        SUPPRESSED.put(part, saved);
        beforeRender(part);
        return () -> {
            if (SUPPRESSED.remove(part) != saved) return;
            try {
                // Respect a freshly injected skin; otherwise restore the last mesh we suppressed.
                if (getMesh.invoke(part) == null && saved.mesh != null)
                    setMesh.invoke(part, saved.mesh, saved.offset);
            } catch (ReflectiveOperationException | LinkageError | RuntimeException e) { report(e); }
        };
    }

    /** Also run before rendering: Skin Layers may inject a new mesh after our model setup callback. */
    public static void beforeRender(ModelPart part) {
        if (!isLoaded()) return;
        SavedMesh saved = SUPPRESSED.get(part);
        if (saved == null) return;
        try {
            Object mesh = getMesh.invoke(part);
            if (mesh != null) {
                saved.mesh = mesh;
                saved.offset = getOffset.invoke(part);
                setMesh.invoke(part, null, null);
            }
        } catch (ReflectiveOperationException | LinkageError | RuntimeException e) { report(e); }
    }

    private static void report(Throwable error) {
        if (!unavailable) MobilityDeviceMod.LOGGER.warn("Wall-run 3D skin fallback is unavailable", error);
        unavailable = true;
    }
}
