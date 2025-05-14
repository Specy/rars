package app.specy.rars;

public class RISCVFile {
    String source;
    String name;

    public RISCVFile(String name, String source) {
        this.source = source;
        this.name = name;
    }

    public String getSource() {
        return source;
    }

    public String getName() {
        return name;
    }
}