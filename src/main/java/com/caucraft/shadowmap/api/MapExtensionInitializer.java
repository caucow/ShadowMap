package com.caucraft.shadowmap.api;

/**
 * Entrypoint interface for a mod to register API users with ShadowMap. To
 * create an extension for ShadowMap, add the {@code "shadowmap"} entrypoint to
 * your {@code fabric.mod.json}, listing an implementation of this interface in
 * the entrypoint array:
 *
 * <pre>
 * {
 *   [...]
 *   "entrypoints": {
 *     "client": [
 *       "com.example.modid.ExampleClientMod"
 *     ]
 *     "shadowmap": [
 *       "com.example.modid.ExampleMapExtension"
 *     ]
 *   }
 *   [...]
 * }
 * </pre>
 *
 * Each entrypoint in the {@code fabric.mod.json} creates its own instance of
 * that entrypoint. Your mod's {@code client} entrypoint should not be the same
 * as its {@code shadowmap} entrypoint.
 */
public interface MapExtensionInitializer {
    /**
     * Called after ShadowMap and most of the Minecraft client have initialized
     * to initialize the map extension and register callbacks and event
     * handlers.<br>
     * <br>
     * Note that the MapApi handle may not be provided by ShadowMap after
     * this is called, so should it be stored by the mod if needed at a later
     * time.
     * @param mapApi the one and only, the great and powerful, "Map A P I"
     */
    void onInitializeMap(MapApi mapApi);

    /**
     * This can be overridden to return a modId other than what is provided by a
     * mod's fabric.mod.json.
     * @return an alternative modId
     * @deprecated this exists to facilitate a transition away from early api
     * access methods and will be removed once ShadowMap's internal structure
     * is finalized.
     * (TODO remove this)
     */
    @Deprecated(forRemoval = true)
    default String modId() {
        return null;
    }
}
