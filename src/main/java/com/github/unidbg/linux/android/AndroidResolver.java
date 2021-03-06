package com.github.unidbg.linux.android;

import com.github.unidbg.Emulator;
import com.github.unidbg.LibraryResolver;
import com.github.unidbg.file.FileResult;
import com.github.unidbg.file.IOResolver;
import com.github.unidbg.file.linux.IOConstants;
import com.github.unidbg.linux.file.DirectoryFileIO;
import com.github.unidbg.linux.file.LogCatFileIO;
import com.github.unidbg.linux.file.SimpleFileIO;
import com.github.unidbg.linux.file.StdoutCallback;
import com.github.unidbg.spi.LibraryFile;
import org.apache.commons.io.FileUtils;
import org.apache.commons.io.FilenameUtils;
import org.apache.commons.io.IOUtils;

import java.io.*;
import java.net.URL;
import java.util.Arrays;
import java.util.List;

public class AndroidResolver implements LibraryResolver, IOResolver {

    private final int sdk;
    private final List<String> needed;

    public AndroidResolver(int sdk, String... needed) {
        this.sdk = sdk;
        this.needed = needed == null ? null : Arrays.asList(needed);
    }

    private StdoutCallback callback;

    @Override
    public void setStdoutCallback(StdoutCallback callback) {
        this.callback = callback;
    }

    public int getSdk() {
        return sdk;
    }

    @Override
    public LibraryFile resolveLibrary(Emulator emulator, String libraryName) {
        if (needed == null) {
            return null;
        }

        if (!needed.isEmpty() && !needed.contains(libraryName)) {
            return null;
        }

        return resolveLibrary(emulator, libraryName, sdk);
    }

    static LibraryFile resolveLibrary(Emulator emulator, String libraryName, int sdk) {
        final String lib = emulator.getPointerSize() == 4 ? "lib" : "lib64";
        String name = "/android/sdk" + sdk + "/" + lib + "/" + libraryName.replace('+', 'p');
        URL url = AndroidResolver.class.getResource(name);
        if (url != null) {
            return new URLibraryFile(url, libraryName, sdk);
        }
        return null;
    }

    @Override
    public FileResult resolve(Emulator emulator, String path, int oflags) {
        File rootDir = emulator.getFileSystem().getRootDir();
        final boolean create = (oflags & IOConstants.O_CREAT) != 0;
        if (path.startsWith("/dev/log/")) {
            try {
                File log = new File(rootDir, path);
                File logDir = log.getParentFile();
                if (!logDir.exists() && !logDir.mkdirs()) {
                    throw new IOException("mkdirs failed: " + logDir);
                }
                if (!log.exists() && !log.createNewFile()) {
                    throw new IOException("create new file failed: " + log);
                }
                return FileResult.success(new LogCatFileIO(oflags, log, path));
            } catch (IOException e) {
                throw new IllegalStateException(e);
            }
        }

        if (".".equals(path)) {
            return createFileIO(emulator.getFileSystem().createWorkDir(), path, oflags);
        }

        File file = new File(rootDir, path);
        if (file.canRead()) {
            return createFileIO(file, path, oflags);
        }
        if (file.getParentFile().exists() && create) {
            return createFileIO(file, path, oflags);
        }

        String androidResource = FilenameUtils.normalize("/android/sdk" + sdk + "/" + path, true);
        InputStream inputStream = AndroidResolver.class.getResourceAsStream(androidResource);
        if (inputStream != null) {
            OutputStream outputStream = null;
            try {
                File tmp = new File(FileUtils.getTempDirectory(), path);
                File dir = tmp.getParentFile();
                if (!dir.exists() && !dir.mkdirs()) {
                    throw new IOException("mkdirs failed: " + dir);
                }
                if (!tmp.exists() && !tmp.createNewFile()) {
                    throw new IOException("createNewFile failed: " + tmp);
                }

                if (tmp.isDirectory()) {
                    return FileResult.success(new DirectoryFileIO(oflags, path));
                }

                outputStream = new FileOutputStream(tmp);
                IOUtils.copy(inputStream, outputStream);
                return createFileIO(tmp, path, oflags);
            } catch (IOException e) {
                throw new IllegalStateException(e);
            } finally {
                IOUtils.closeQuietly(outputStream);
                IOUtils.closeQuietly(inputStream);
            }
        }

        return null;
    }

    private FileResult createFileIO(File file, String pathname, int oflags) {
        if (file.canRead()) {
            return FileResult.success(file.isDirectory() ? new DirectoryFileIO(oflags, pathname) : new SimpleFileIO(oflags, file, pathname));
        }

        return null;
    }

}
