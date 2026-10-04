package app.specy.rars.assembler;

import app.specy.rars.AssemblyException;
import app.specy.rars.RISCVprogram;
import app.specy.rars.riscv.fs.MemoryFileSystem;
import app.specy.rars.riscv.fs.SourcePath;

import java.util.HashMap;
import java.util.Map;

/**
 * A library of GNU compiler assembly members and the index naming the member that defines each
 * global symbol. A Build pulls a member only to resolve a global the program uses and does not
 * define, as ld pulls members out of an archive. Members live in their own file system, so their
 * paths never meet the program's Files.
 */
public final class RuntimeLibrary {
    private final MemoryFileSystem files = new MemoryFileSystem();
    private final Map<String, String> index = new HashMap<>();
    private final java.util.Set<String> resolveWeak = new java.util.HashSet<>();

    /**
     * @param paths   canonical member paths
     * @param sources member sources, parallel to {@code paths}
     * @param symbols indexed global symbols
     * @param members the member defining each symbol, parallel to {@code symbols}
     */
    public RuntimeLibrary(String[] paths, String[] sources, String[] symbols, String[] members) {
        this(paths, sources, symbols, members, new String[0]);
    }

    /**
     * @param resolveWeak globals the library supplies even when a program refers to them only
     *                    weakly, which otherwise pulls no member
     */
    public RuntimeLibrary(String[] paths, String[] sources, String[] symbols, String[] members, String[] resolveWeak) {
        if (paths == null || sources == null || paths.length != sources.length)
            throw new IllegalArgumentException("Library member paths and sources must have the same length");
        if (symbols == null || members == null || symbols.length != members.length)
            throw new IllegalArgumentException("Library index symbols and members must have the same length");
        for (int i = 0; i < paths.length; i++) {
            SourcePath.requireCanonical(paths[i]);
            if (sources[i] == null) throw new IllegalArgumentException("Library member source must be a string: " + paths[i]);
            if (files.exists(paths[i])) throw new IllegalArgumentException("Duplicate library member: " + paths[i]);
            files.write(paths[i], sources[i]);
        }
        for (int i = 0; i < symbols.length; i++) {
            if (symbols[i] == null || symbols[i].isEmpty()) throw new IllegalArgumentException("Library index symbols must be names");
            if (!files.exists(members[i])) throw new IllegalArgumentException("Library index names a missing member: " + members[i]);
            // The first member to claim a symbol keeps it, as the first archive on an ld command line would.
            index.putIfAbsent(symbols[i], members[i]);
        }
        if (resolveWeak != null) for (String name : resolveWeak) if (index.containsKey(name)) this.resolveWeak.add(name);
    }

    /** The member that defines {@code symbol}, or null. */
    public String memberFor(String symbol) {
        return index.get(symbol);
    }

    public boolean defines(String symbol) {
        return index.containsKey(symbol);
    }

    /** Whether a weak reference to {@code symbol} pulls its member all the same. */
    public boolean resolvesWeak(String symbol) {
        return resolveWeak.contains(symbol);
    }

    /** Reads and tokenizes one member as GNU compiler assembly. */
    public RISCVprogram load(String path) throws AssemblyException {
        RISCVprogram program = new RISCVprogram();
        program.prepareForAssembly(path, files, AssemblerProfile.GNU_COMPILER_V1);
        return program;
    }
}
