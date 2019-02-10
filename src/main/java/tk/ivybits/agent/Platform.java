package tk.ivybits.agent;

public enum Platform {
    LINUX, WINDOWS, MAC, SOLARIS;

    public static Platform getPlatform() {
        String os = System.getProperty("os.name").toLowerCase();
        if (os.indexOf("win") >= 0) {
            return WINDOWS;
        }
        if ((os.indexOf("nix") >= 0) || (os.indexOf("nux") >= 0) || (os.indexOf("aix") > 0)) {
            return LINUX;
        }
        if (os.indexOf("mac") >= 0) {
            return MAC;
        }
        if (os.indexOf("sunos") >= 0)
            return SOLARIS;
        return null;
    }

    public static boolean is64Bit() {
        String osArch = System.getProperty("os.arch");
        return "amd64".equals(osArch) || "x86_64".equals(osArch);
    }
}
