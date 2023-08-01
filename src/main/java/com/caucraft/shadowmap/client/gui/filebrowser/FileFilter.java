package com.caucraft.shadowmap.client.gui.filebrowser;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.function.Predicate;

public record FileFilter(String description, Predicate<Path> filter) {
    public static final FileFilter ALL_FILES = new FileFilter("All Files", (path) -> true);
    public static final FileFilter ALL_DIRECTORIES = new FileFilter("All Folders", Files::isDirectory);
}
