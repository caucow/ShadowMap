package com.caucraft.shadowmap.client.gui.filebrowser;

import com.caucraft.shadowmap.client.ShadowMap;
import com.caucraft.shadowmap.client.gui.LessPoopScreen;
import com.caucraft.shadowmap.client.util.OsType;
import net.minecraft.text.Text;
import org.apache.logging.log4j.Logger;

import java.io.IOException;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class FileBrowser extends LessPoopScreen {
    private static final Logger LOGGER = ShadowMap.getLogger();

    private ArrayDeque<Path> pathHistory;

    /*
    UI Layout:
    - [TOP] Nav bar: [Back] [Forwards] [b/f stack select dropdown] [Up to Parent] [New Folder] [--- Address Bar ---] [Common Addresses Dropdown] [Refresh/Go]
    - [LEFT] Quick Access Pane: [User Home] [User Desktop] [User Downloads] [User Documents n stuff...] [System/Drive Root Directories...]
    - [BOTTOM] File Selection Pane
        - [TOP] File Entry Pane
        - [BOTTOM]
    - File Browser Pane: bog standard, make it responsive, make it not suck, loading wheel if loading a large dir, etc.
    - File Name Selector: Allow single- or multi-name selection (default single)
    - File Type Selector: All by default, else filter File Browser Pane for dirs/matching extensions
    - Confirm Buttons: [Save/Open] [Cancel]
     */



    public FileBrowser(String title, FileBrowser.Type type) {
        super(Text.of(title));
        this.pathHistory = new ArrayDeque<>(32);
    }

    private static Path[] getRoots() {
        List<Path> roots = new ArrayList<>();
        for (Path p : FileSystems.getDefault().getRootDirectories()) {
            roots.add(p);
        }

        switch (OsType.SYSTEM_OS) {
            case WINDOWS -> {} // getRootDirs is good enough for windows
            case LINUX -> { // Read /proc/mounts, parse out media mount points
                try {
                    List<String> lines = Files.readAllLines(Paths.get("/proc/mounts"));
                    // 6 space-separated columns, column 2/6 might have whitespace
                    Pattern mountPattern = Pattern.compile("\\S+ (?<m>\\S+(\\s+\\S+)*?) \\S+ \\S+ \\S+ \\S+");
                    if (lines.isEmpty()) {
                        LOGGER.warn(
                                "Could not find mounted media (including the system drive) in /proc/mounts, is a common prefix missing?");
                    }
                    for (String line : lines) {
                        Matcher lineMatch = mountPattern.matcher(line);
                        if (!lineMatch.matches()) {
                            LOGGER.warn("Could not parse /proc/mounts output for mount point: " + line);
                            continue;
                        }
                        String mount = lineMatch.group("m");
                        if (mount.startsWith("/media/") || mount.startsWith("/run/media/")) {
                            roots.add(Paths.get(mount));
                        }
                    }
                } catch (IOException ex) {
                    LOGGER.warn("Could not read /proc/mounts for mounted media");
                }
            }
            case MAC -> {
                Path volumes = Paths.get("/Volumes");
                if (!Files.isDirectory(volumes)) {
                    LOGGER.warn("Volumes directory is not a directory?");
                    break;
                }
                try {
                    Files.list(volumes).forEach(roots::add);
                } catch (IOException ex) {
                    LOGGER.warn("Could not read Volumes directory for file system roots", ex);
                }
            }
        }
        return roots.toArray(new Path[roots.size()]);
    }

    private static Path getDesktop() {
        return Paths.get(System.getProperty("user.home") + "/Desktop");
    }

    private static Path getDownloads() {
        return Paths.get(System.getProperty("user.home") + "/Desktop");
    }

    public enum Type {
        SAVE,
        OPEN,
        OPEN_MULTIPLE;
    }
}
