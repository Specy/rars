package app.specy.rars.riscv.fs;

import app.specy.rars.RISCVFile;

import java.util.List;

public abstract class RISCVFileSystem {
    abstract public String read(String path);
    abstract public void write(String path, String content);
    abstract public void delete(String path);
    abstract public void create(String path);
    abstract public List<RISCVFile> getFiles();
    public void write(RISCVFile file) {
        this.write(file.getName(), file.getSource());
    }
    public void create(RISCVFile file) {
        this.create(file.getName());
    }
    public void readMipsFile(String path) {
        new RISCVFile(path, this.read(path));
    }
}
