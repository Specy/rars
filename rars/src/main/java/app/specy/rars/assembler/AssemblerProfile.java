package app.specy.rars.assembler;

/** Immutable, per-program assembly dialect. Never inferred from source contents. */
public enum AssemblerProfile {
    RARS("rars"), GNU_COMPILER_V1("gnu-compiler-v1");

    private final String id;
    AssemblerProfile(String id) { this.id = id; }
    public String getId() { return id; }
    public static AssemblerProfile parse(String id) {
        for (AssemblerProfile profile : values()) if (profile.id.equals(id)) return profile;
        throw new IllegalArgumentException("Unsupported assembler profile: " + id);
    }
}
