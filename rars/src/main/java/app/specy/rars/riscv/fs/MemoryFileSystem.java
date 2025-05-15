package app.specy.rars.riscv.fs;

import app.specy.rars.RISCVFile;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class MemoryFileSystem extends RISCVFileSystem {
    private Map<String, String> files = new HashMap<String, String>();

    public String read(String path) {
        String f = files.get(path);
        if (f == null) {
            throw new RuntimeException("File not found: " + path);
        }
        return f;
    }

    public void write(String path, String content) {
        files.put(path, content);
    }

    public void delete(String path) {
        files.remove(path);
    }

    public void create(String path) {
        this.write(path, "");
    }

    public MemoryFileSystem addFile(String path, String content) {
        files.put(path, content);
        return this;
    }

    public List<RISCVFile> getFiles() {
        return files.entrySet().stream().map(e -> new RISCVFile(e.getKey(), e.getValue())).toList();
    }

}
