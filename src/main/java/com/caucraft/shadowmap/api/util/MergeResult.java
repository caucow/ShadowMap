package com.caucraft.shadowmap.api.util;

/**
 * Represents the result of a region or chunk merge operation. Instances of
 * MergeResult are immutable, so changing a result should be done by reassigning
 * its variable and/or chaining result methods. This should reflect which parts
 * of "this" and the "other" instance of a class were used, and whether "this"
 * instance changed in a way that could require re-rendering the chunk or region
 * as a result of including data from either instance. This helps ShadowMap
 * determine whether saved and loaded map files are outdated and whether a
 * merged chunk or region needs to be re-rendered as a result of the merge.<br>
 * <br>
 * An instance of MergeResult should be updated throughout the merge process
 * following these guidelines:<br>
 * <ul>
 *     <li>If the data in "other" was modified more recently and added to
 *     the data in memory ("this"), set the result flag "used other"</li>
 *     <li>If the data in memory ("this") was modified more recently or is
 *     not completely discarded during the merge, set the result flag "used
 *     this"</li>
 *     <li>If the data in memory changed in a way that requires the region
 *     or chunk to be re-rendered (such as a block or chunk overlay of the
 *     data), set the result to "render needed"</li>
 * </ul>
 * Use {@link MergeResult#getResult()} to get an instance of MergeResult.<br>
 */
public class MergeResult {
    private static final int FLAG_USE_THIS = 0x01;
    private static final int FLAG_USE_OTHER = 0x02;
    private static final int FLAG_RENDER_NEEDED = 0x04;
    private static final MergeResult[] RESULTS;

    static {
        RESULTS = new MergeResult[8];
        for (int i = 0; i < 8; i++) {
            RESULTS[i] = new MergeResult(i);
        }
    }

    private final int flags;

    private MergeResult(int flags) {
        this.flags = flags;
    }

    /**
     * @return a MergeResult with no flags set.
     */
    public static MergeResult getResult() {
        return RESULTS[0];
    }

    /**
     * ORs the merge flags from this and the other result.
     * @param otherResult the other more different result.
     * @return a new merge result with flags from both results set.
     */
    public MergeResult includeResult(MergeResult otherResult) {
        return RESULTS[flags | otherResult.flags];
    }

    /**
     * Sets the merge flag for using part of "this" instance.
     * @return a newmerge result with the "used this" set.
     */
    public MergeResult usedThis() {
        return RESULTS[flags | FLAG_USE_THIS];
    }

    /**
     * Sets the merge flag for using part of the "other" instance.
     * @return a new merge result with the "used other" flag set.
     */
    public MergeResult usedOther() {
        return RESULTS[flags | FLAG_USE_OTHER];
    }

    /**
     * Sets the merge flag for using parts of both "this" and the "other"
     * instance.
     * @return a new merge result with both usage flags set.
     */
    public MergeResult usedBoth() {
        return RESULTS[flags | FLAG_USE_THIS | FLAG_USE_OTHER];
    }

    /**
     * Sets the merge flag for using part of "this" instance.
     * @return a new merge result with the render flag set.
     */
    public MergeResult renderNeeded() {
        return RESULTS[flags | FLAG_RENDER_NEEDED];
    }

    /**
     * @return true if the merge result had no flags set.
     */
    public boolean isNothingHappened() {
        return flags == 0;
    }

    /**
     * @return true if the merge used "this" instance.
     */
    public boolean isUsedThis() {
        return (flags & FLAG_USE_THIS) != 0;
    }

    /**
     * @return true if the merge used the "other" instance.
     */
    public boolean isUsedOther() {
        return (flags & FLAG_USE_OTHER) != 0;
    }

    /**
     * @return true if the merge used parts of both instances.
     */
    public boolean isUsedBoth() {
        int combo = FLAG_USE_OTHER | FLAG_USE_THIS;
        return (flags & combo) == combo;
    }

    /**
     * @return true if the merge caused a change that requires the map
     * region or chunk to be re-rendered.
     */
    public boolean isRenderNeeded() {
        return (flags & FLAG_RENDER_NEEDED) != 0;
    }
}
