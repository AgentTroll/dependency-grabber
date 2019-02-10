package tk.ivybits.agent;

import java.io.*;
import java.lang.management.ManagementFactory;
import java.lang.reflect.Field;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Paths;

/**
 * Various IO tools.
 *
 * @author Tudor
 */
public class Tools {
    private static final String REV = "1";
    private static final String NATIVE_DIR = "natives/";
    private static final String WIN_DIR = "windows/";
    private static final String NIX_DIR = "linux/";
    private static final String MAC_DIR = "mac/";
    private static final String SOLARIS_DIR = "solaris/";
    private static final String CACHE_DIR = System.getProperty("java.io.tmpdir") + File.separatorChar + "agentcache.0.0_" + REV;

    /**
     * Gets the current JVM PID.
     *
     * @return Returns the PID.
     */
    public static String getCurrentPID() {
        String jvm = ManagementFactory.getRuntimeMXBean().getName();
        return jvm.substring(0, jvm.indexOf('@'));
    }

    public static byte[] getBytesFromStream(InputStream stream) throws IOException {
        ByteArrayOutputStream buffer = new ByteArrayOutputStream();
        int nRead;
        byte[] data = new byte[65536];
        while ((nRead = stream.read(data, 0, data.length)) != -1) {
            buffer.write(data, 0, nRead);
        }
        buffer.flush();
        return buffer.toByteArray();

    }

    /**
     * Gets bytes from class
     *
     * @param clazz The class.
     * @return Returns a byte[] representation of given class.
     * @throws IOException
     */
    public static byte[] getBytesFromClass(Class<?> clazz) throws IOException {
        return getBytesFromStream(clazz.getClassLoader().getResourceAsStream(clazz.getName().replace('.', '/') + ".class"));
    }

    /**
     * Gets bytes from resource
     *
     * @param resource The resource string.
     * @return Returns a byte[] representation of given resource.
     * @throws IOException
     */
    public static byte[] getBytesFromResource(ClassLoader clazzLoader, String resource) throws IOException {
        return getBytesFromStream(clazzLoader.getResourceAsStream(resource));
    }

    /**
     * Adds a a path to the current java.library.path.
     *
     * @param path The path.
     * @throws SecurityException
     * @throws NoSuchFieldException
     * @throws IllegalAccessException
     * @throws IllegalArgumentException
     */
    public static void addToLibPath(String path) throws NoSuchFieldException, SecurityException, IllegalArgumentException, IllegalAccessException {
        if (System.getProperty("java.library.path") != null) {
            // If java.library.path is not empty, we will prepend our path
            // Note that path.separator is ; on Windows and : on *nix,
            // so we can't hard code it.
            System.setProperty("java.library.path", path + System.getProperty("path.separator") + System.getProperty("java.library.path"));
        } else {
            System.setProperty("java.library.path", path);
        }

        // Important: java.library.path is cached
        // We will be using reflection to clear the cache
        Field fieldSysPath = ClassLoader.class.getDeclaredField("sys_paths");
        fieldSysPath.setAccessible(true);
        fieldSysPath.set(null, null);

    }

    /**
     * Extracts a resource to specified path.
     *
     * @param loader
     * @param resourceName
     * @param targetName
     * @param targetDir
     * @throws IOException
     */
    public static void extractResourceToDirectory(ClassLoader loader, String resourceName, String targetName, String targetDir)
            throws IOException {
        InputStream source = loader.getResourceAsStream(resourceName);
        File tmpdir = new File(targetDir);
        File target = new File(tmpdir, targetName);
        target.createNewFile();

        FileOutputStream stream = new FileOutputStream(target);
        byte[] buf = new byte[65536];
        int read;
        while ((read = source.read(buf)) != -1) {
            stream.write(buf, 0, read);
        }
        stream.close();
        source.close();
    }

    /**
     * Attempts to load an attach library.
     */
    public static void loadAgentLibrary() {
        Platform i = Platform.getPlatform();
        if (i == Platform.WINDOWS) {
            unpack(WIN_DIR + "attach.dll");
        } else if (i == Platform.LINUX) {
            unpack(NIX_DIR + "libattach.so");
        } else if (i == Platform.MAC) {
            unpack(MAC_DIR + "libattach.dylib");
        } else if (i == Platform.SOLARIS) {
            unpack(SOLARIS_DIR + "libattach.so");
        } else {
            throw new UnsupportedOperationException("unsupported platform");
        }
    }

    private static void unpack(String path) {
        try {
            // System.out.println(NATIVE_DIR + ((Platform.is64Bit() || Platform.getPlatform() == Platform.MAC) ? "64/" : "32/") + path);
            URL url = ClassLoader.getSystemResource(NATIVE_DIR + ((Platform.is64Bit() || Platform.getPlatform() == Platform.MAC) ? "64/" : "32/") + path);

            File pathDir = new File(CACHE_DIR);
            pathDir.mkdirs();
            File libfile = new File(pathDir, path.substring(path.lastIndexOf("/"), path.length()));

            if (!libfile.exists()) {
                libfile.deleteOnExit();
                // InputStream in = url.openStream();
                InputStream in = Files.newInputStream(Paths.get(System.getProperty("java.home"), "lib/amd64/libattach.so"));
                OutputStream out = new BufferedOutputStream(new FileOutputStream(libfile));

                int len;
                byte[] buffer = new byte[8192];
                while ((len = in.read(buffer)) > -1) {
                    out.write(buffer, 0, len);
                }
                out.flush();
                out.close();
                in.close();
            }
        } catch (IOException x) {
            throw new RuntimeException("could not unpack binaries", x);
        }
    }
}
