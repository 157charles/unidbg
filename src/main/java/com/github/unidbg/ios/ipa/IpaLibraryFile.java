package com.github.unidbg.ios.ipa;

import com.github.unidbg.Emulator;
import com.github.unidbg.spi.LibraryFile;
import org.apache.commons.io.FilenameUtils;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import java.io.File;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.Arrays;
import java.util.List;

public class IpaLibraryFile implements LibraryFile {

    private static final Log log = LogFactory.getLog(IpaLibraryFile.class);

    private final String appDir;
    private final File ipa;
    private final String executable;
    private final byte[] data;

    IpaLibraryFile(String appDir, File ipa, String executable, String... loads) throws IOException {
        this(appDir, ipa, executable, IpaLoader.loadZip(ipa, appDir + executable), Arrays.asList(loads));
    }

    private final List<String> loadList;

    private IpaLibraryFile(String appDir, File ipa, String executable, byte[] data, List<String> loadList) {
        this.appDir = appDir;
        this.ipa = ipa;
        this.executable = executable;
        this.data = data;
        this.loadList = loadList;
    }

    @Override
    public String getName() {
        return executable;
    }

    @Override
    public String getMapRegionName() {
        return executable;
    }

    @Override
    public LibraryFile resolveLibrary(Emulator emulator, String soName) throws IOException {
        String path = soName.replace("@rpath", appDir + "Frameworks");
        if (log.isDebugEnabled()) {
            log.debug("Try resolve library soName=" + soName + ", path=" + path);
        }
        if (!loadList.isEmpty() && !loadList.contains(FilenameUtils.getName(path))) {
            return null;
        }
        byte[] libData = IpaLoader.loadZip(ipa, path);
        return libData == null ? null : new IpaLibraryFile(appDir, ipa, soName, libData, loadList);
    }

    @Override
    public byte[] readToByteArray() {
        return data;
    }

    @Override
    public ByteBuffer mapBuffer() {
        return ByteBuffer.wrap(data);
    }

    @Override
    public String getPath() {
        return appDir + executable;
    }

}
