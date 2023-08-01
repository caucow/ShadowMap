package com.caucraft.shadowmap.client.util;

public enum OsType {
    WINDOWS, MAC, LINUX, UNKNOWN;

    public static final OsType SYSTEM_OS;

    static {
        String osName = System.getProperty("os.name").toLowerCase();
        if (osName.contains("windows")) {
            SYSTEM_OS = OsType.WINDOWS;
        } else if (osName.contains("linux")) {
            SYSTEM_OS = OsType.LINUX;
        } else if (osName.contains("os x") || osName.contains("osx")) {
            SYSTEM_OS = OsType.MAC;
        } else {
            SYSTEM_OS = OsType.UNKNOWN;
        }
    }
}
