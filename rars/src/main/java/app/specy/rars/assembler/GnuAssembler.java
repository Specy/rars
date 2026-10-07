package app.specy.rars.assembler;

import app.specy.rars.*;
import app.specy.rars.riscv.*;
import app.specy.rars.riscv.fs.*;
import app.specy.rars.riscv.hardware.*;
import app.specy.rars.util.JavaNumberText;
import app.specy.rars.util.SystemIO;
import app.specy.rars.util.Utf8;
import java.io.ByteArrayOutputStream;
import java.math.BigInteger;
import java.util.*;
import java.util.regex.*;

/**
 * Bounded, static GNU compiler assembly. Each translation unit is parsed on its own:
 * parsing never writes memory, instruction expansions have fixed sizes before section
 * layout, and expressions retain their section identity until fixup resolution. Units
 * are then linked with ld semantics: sections of one family are placed in unit order,
 * a unit's own symbols resolve before global ones, a strong global definition beats a
 * weak one, an undefined weak reference is zero, and only the first unit's copy of a
 * COMDAT group is kept. This is not an ELF linker: there are no object files,
 * relocation records or linker scripts.
 */
public final class GnuAssembler {
    private static final int MAX_BYTES = 16 * 1024 * 1024;
    /** The page size the heap is aligned to when it follows static data, as a kernel aligns the break. */
    private static final long PAGE_BYTES = 4096;
    private static final BigInteger ZERO = BigInteger.ZERO, ONE = BigInteger.ONE;
    // Section families, in layout order. Text is the only executable family, writable
    // families start at INIT and zeroed ones at BSS.
    private static final int TEXT = 0, RODATA = 1, INIT = 2, FINI = 3, DATA = 4, BSS = 5, COMMON = 6;
    private static final String NAME_TEXT = "[.$A-Za-z_][.$A-Za-z_0-9]*";
    private static final Pattern NAME = Pattern.compile(NAME_TEXT);
    private static final Pattern LABEL = Pattern.compile("^(" + NAME_TEXT + "|[0-9]+)\\s*:");
    private static final Pattern LEX = Pattern.compile("\"(?:\\\\.|[^\"\\\\])*\"|#[^\\n]*|[.$A-Za-z_%][.$A-Za-z_0-9%]*|(?:0[xX][0-9a-fA-F]+|[0-9]+[bf]?)|[^\\s,]");
    private static final Pattern MODIFIER = Pattern.compile("%(pcrel_hi|pcrel_lo|hi|lo)\\(");
    private static final Pattern ANY_MODIFIER = Pattern.compile("%([a-zA-Z_]+)\\(");
    private static final Pattern SUPPORTED_MODIFIER = Pattern.compile("hi|lo|pcrel_hi|pcrel_lo");
    private static final Pattern ASSIGNMENT = Pattern.compile("^(" + NAME_TEXT + ")\\s*=\\s*(.+)$");
    private static final Pattern SET_DIRECTIVE = Pattern.compile("^\\.(?:set|equ)\\s+(" + NAME_TEXT + ")\\s*,\\s*(.+)$");
    private static final Pattern INCLUDE = Pattern.compile("\\.include(?:\\s.*)?");
    private static final Pattern DIGITS = Pattern.compile("[0-9]+");
    private static final Pattern NUMERIC_REFERENCE = Pattern.compile("[0-9]+[bf]");
    private static final Pattern PCREL_LO_ANCHOR = Pattern.compile(NAME_TEXT + "|[0-9]+[bf]");
    /** Symbol types; a C++ inline variable is a gnu_unique_object, which a static link treats as its COMDAT group does. */
    private static final Pattern SYMBOL_TYPE = Pattern.compile("[@%](function|object|notype|gnu_unique_object)");
    private static final Pattern IGNORED_CFI = Pattern.compile("\\.cfi_(startproc|endproc|def_cfa_offset|def_cfa|def_cfa_register|offset|restore|remember_state|restore_state|sections|undefined|same_value|return_column|signal_frame|adjust_cfa_offset|escape)");
    private static final Pattern TEXT_SECTION = Pattern.compile("\\.text(?:\\..*)?");
    private static final Pattern RODATA_SECTION = Pattern.compile("\\.(rodata|srodata|rdata)(?:\\..*)?");
    private static final Pattern DATA_SECTION = Pattern.compile("\\.(data|sdata)(?:\\..*)?");
    private static final Pattern BSS_SECTION = Pattern.compile("\\.(bss|sbss)(?:\\..*)?");
    private static final Pattern DEBUG_SECTION = Pattern.compile("\\.(debug_.*|zdebug_.*|comment|note\\.GNU-stack|riscv\\.attributes|llvm_addrsig)");
    private static final Pattern SECTION_FLAGS = Pattern.compile("[awxMSG]*");
    private static final Pattern ENTRY_SIZE = Pattern.compile("[1-9][0-9]*");
    private static final Pattern ARCH_EXTENSIONS = Pattern.compile("(?:2p[01])?(?:_(?:m2p0|f2p[02]|d2p[02]|zicsr2p0|zifencei2p0|zmmul1p0))*");
    private static final Pattern ATOMIC = Pattern.compile("(?:lr|sc|amo[a-z]+)\\..*");
    private static final Pattern MEMORY_ACCESS = Pattern.compile("(lb|lbu|lh|lhu|lw|lwu|ld|flw|fld|sb|sh|sw|sd|fsw|fsd)");
    private static final Pattern ADDRESS_TEMPLATE = Pattern.compile(".*(PCH|PCL|LH|LL|VH|VL)[0-9].*");
    private static final Pattern BRANCH = Pattern.compile("beq|bne|blt|bge|bltu|bgeu|jal");
    private static final Pattern ROUNDING_MODE = Pattern.compile("rne|rtz|rdn|rup|rmm|dyn");
    /** A constructor or destructor array section with an explicit priority. */
    private static final Pattern PRIORITIZED_ARRAY = Pattern.compile("\\.(?:init|fini)_array\\.(\\d{1,5})");
    /** A lowered conditional branch: its operation, its two registers, and its target. */
    private static final Pattern CONDITIONAL_BRANCH = Pattern.compile("(beq|bne|blt|bge|bltu|bgeu) ([^,]+,[^,]+),(.+)");
    private static final Map<String, String> INVERSE_BRANCH = new HashMap<>();
    static {
        INVERSE_BRANCH.put("beq", "bne"); INVERSE_BRANCH.put("bne", "beq");
        INVERSE_BRANCH.put("blt", "bge"); INVERSE_BRANCH.put("bge", "blt");
        INVERSE_BRANCH.put("bltu", "bgeu"); INVERSE_BRANCH.put("bgeu", "bltu");
    }
    /** A name in an operand or expression; a leading % marks an address modifier, not a name. */
    private static final Pattern REFERENCE = Pattern.compile("0[xX][0-9a-fA-F]+|[0-9]+[bf]?|%?[.$A-Za-z_][.$A-Za-z_0-9]*");

    private final ErrorList errors = new ErrorList();
    private final List<Unit> units = new ArrayList<>();
    /** The definition every unit sees for a global name: strong, or else the first weak one. */
    private final Map<String, Symbol> globals = new HashMap<>();
    private final Map<Long, HighFixup> highs = new HashMap<>();
    private final List<Fragment> textGaps = new ArrayList<>();
    /** The merged range of each array family, whose ends are the ld-provided bounds. */
    private final Map<Integer, Section> arrayRanges = new HashMap<>();
    private final RuntimeLibrary library;
    /** A global the link must define, as ld's entry symbol; it also pulls its library member. */
    private final String entrySymbol;
    /**
     * Whether a RARS-dialect program's global symbol table also supplies definitions: true when
     * library members are linked after a program the legacy assembler is assembling.
     */
    private boolean legacyGlobals;
    private Section legacyText, legacyData;
    /** Where the linked program's heap starts; see {@link #heapStart()}. */
    private long heapStart;

    private final class Section {
        final Unit unit;
        final String name, flags, type, entrySize, group;
        final int family;
        int size, alignment;
        long base;
        /** A later unit's copy of a COMDAT group that an earlier unit already supplied. */
        boolean duplicateGroup;
        /**
         * Where an .init_array or .fini_array section goes among the others: `.init_array.00200` is
         * priority 200, and the plain section comes after every numbered one, as ld sorts them.
         */
        int priority = 65536;
        final List<Fragment> fragments = new ArrayList<>();
        Section(Unit unit, String name, String flags, String type, String entrySize, String group, int family) {
            this.unit = unit; this.name = name; this.flags = flags; this.type = type; this.family = family;
            this.entrySize = entrySize; this.group = group;
            alignment = family == TEXT ? 4 : 1;
        }
        boolean executable() { return family == TEXT; }
        boolean discarded() { return family < 0 || duplicateGroup; }
        boolean zeroed() { return type.equals("@nobits"); }
    }
    private static final class Fragment {
        final Section section;
        final int line, offset, size;
        final String instruction;
        final List<String> expressions;
        final int width;
        final byte[] bytes;
        final int fill;
        Fragment(Section section, int line, int offset, int size, String instruction,
                 List<String> expressions, int width, byte[] bytes, int fill) {
            this.section = section; this.line = line; this.offset = offset; this.size = size;
            this.instruction = instruction; this.expressions = expressions; this.width = width;
            this.bytes = bytes; this.fill = fill;
        }
        long address() { return section.base + offset; }
    }
    private static final class Symbol {
        final Unit unit;
        final String name;
        final Section section;
        final int offset, line;
        final String alias;
        Symbol(Unit unit, String name, Section section, int offset, int line, String alias) {
            this.unit = unit; this.name = name; this.section = section; this.offset = offset; this.line = line; this.alias = alias;
        }
    }
    private static final class Value {
        final BigInteger number;
        final Section section;
        final boolean symbolic;
        Value(BigInteger number, Section section, boolean symbolic) {
            this.number = number; this.section = section; this.symbolic = symbolic;
        }
    }
    private static final class HighFixup {
        final Fragment fragment;
        final String expression;
        HighFixup(Fragment fragment, String expression) { this.fragment = fragment; this.expression = expression; }
    }
    private static final class Invalid extends RuntimeException {
        Invalid(String message) { super(message); }
    }

    /** Assembles one translation unit. */
    public GnuAssembler(RISCVprogram program) { this(List.of(program), null, null); }
    /** Assembles and links translation units in link order. */
    public GnuAssembler(List<RISCVprogram> programs) { this(programs, null, null); }
    /**
     * Assembles and links translation units in link order, followed by the library members they
     * need. {@code library} and {@code entrySymbol} may be null.
     */
    public GnuAssembler(List<RISCVprogram> programs, RuntimeLibrary library, String entrySymbol) {
        this.library = library;
        this.entrySymbol = entrySymbol == null || entrySymbol.isEmpty() ? null : entrySymbol;
        for (RISCVprogram program : programs) units.add(new Unit(program, units.size()));
    }
    public ErrorList getErrors() { return errors; }

    /**
     * Where the linked program's heap, and so the first block sbrk hands out, starts: RARS's heap
     * base, or, once static data reaches past it, the first page after static data, where ld and
     * a kernel put the break after .bss. A RARS-dialect program linking members keeps RARS's heap
     * base. Valid after a successful {@link #assemble()} or {@link #linkAfter}.
     */
    public int heapStart() { return (int) heapStart; }

    /** The global symbols one GNU unit defines and the ones it needs from elsewhere. */
    public static final class UnitSymbols {
        /** Globals the unit defines, strong or weak. */
        public final String[] defined;
        /** The subset of {@code defined} that is weak. */
        public final String[] weak;
        /** Globals the unit uses without defining them, other than weak references. */
        public final String[] references;
        UnitSymbols(String[] defined, String[] weak, String[] references) {
            this.defined = defined; this.weak = weak; this.references = references;
        }
    }

    /**
     * Parses one GNU unit without laying it out, for building a library index: which globals it
     * defines and which it pulls from elsewhere. The instruction set and width must be initialized.
     */
    public static UnitSymbols analyze(String path, String source) throws AssemblyException {
        app.specy.rars.riscv.fs.MemoryFileSystem files = new app.specy.rars.riscv.fs.MemoryFileSystem();
        files.write(path, source);
        RISCVprogram program = new RISCVprogram();
        program.prepareForAssembly(path, files, AssemblerProfile.GNU_COMPILER_V1);
        GnuAssembler assembler = new GnuAssembler(program);
        Unit unit = assembler.units.get(0);
        unit.parseAll();
        assembler.fail();
        List<String> defined = new ArrayList<>(unit.definedGlobals()), weak = new ArrayList<>();
        for (String name : defined) if (unit.weak.contains(name)) weak.add(name);
        return new UnitSymbols(defined.toArray(new String[0]), weak.toArray(new String[0]),
                unit.strongReferences().toArray(new String[0]));
    }

    /** Expand includes without the educational tokenizer rejecting GNU expressions. */
    public static ArrayList<TokenList> prepare(RISCVprogram program, RISCVFileSystem files) throws AssemblyException {
        ArrayList<SourceLine> lines = new ArrayList<>();
        ErrorList errors = new ErrorList();
        expand(program, files, new ArrayList<>(), lines, errors);
        program.setSourceLineList(lines);
        ArrayList<TokenList> tokens = new ArrayList<>();
        for (int i = 0; i < lines.size(); i++) {
            SourceLine line = lines.get(i);
            TokenList list = new TokenList();
            list.setProcessedLine(line.getSource());
            Matcher matcher = LEX.matcher(line.getSource());
            while (matcher.find()) {
                String text = matcher.group();
                TokenTypes type = TokenTypes.matchTokenType(text);
                if (type == null || type == TokenTypes.ERROR) type = TokenTypes.IDENTIFIER;
                Token token = new Token(type, text, program, i + 1, matcher.start() + 1);
                token.setOriginal(line.getRISCVprogram(), line.getLineNumber());
                list.add(token);
            }
            tokens.add(list);
        }
        if (errors.errorsOccurred()) throw new AssemblyException(errors);
        return tokens;
    }
    private static void expand(RISCVprogram source, RISCVFileSystem files, List<String> stack,
                               ArrayList<SourceLine> output, ErrorList errors) throws AssemblyException {
        if (stack.size() >= 64 || stack.contains(source.getFilename())) {
            throw new IllegalArgumentException("Recursive or excessive include: " + source.getFilename());
        }
        stack.add(source.getFilename());
        for (int i = 0; i < source.getSourceList().size(); i++) {
            String line = source.getSourceList().get(i);
            String text = uncomment(line).trim();
            output.add(new SourceLine(line, source, i + 1));
            if (!INCLUDE.matcher(text).matches()) continue;
            try {
                List<String> args = arguments(text.substring(8));
                require(args.size() == 1, ".include requires one quoted path");
                String path = SourcePath.resolveInclude(source.getFilename(), unquote(args.get(0)));
                require(!stack.contains(path) && stack.size() < 64, "Recursive or excessive include: " + path);
                RISCVprogram child = new RISCVprogram();
                child.readSource(path, files.read(path));
                expand(child, files, stack, output, errors);
            } catch (RuntimeException exception) {
                errors.add(new ErrorMessage(source, i + 1, 1, exception.getMessage()));
            }
        }
        stack.remove(stack.size() - 1);
    }

    /** Parses every unit, links them and writes the program to memory. Returns the executable statements by address. */
    public ArrayList<ProgramStatement> assemble() throws AssemblyException {
        for (Unit unit : units) unit.parseAll();
        fail();
        Set<String> required = new LinkedHashSet<>();
        if (entrySymbol != null) required.add(entrySymbol);
        resolveMembers(required);
        return link(Integer.toUnsignedLong(Memory.textBaseAddress), Integer.toUnsignedLong(Memory.dataBaseAddress));
    }

    /**
     * Links the library members a RARS-dialect program needs, after that program's first pass:
     * {@code undefined} are the globals it uses without defining, and the members are placed after
     * its text and data. The members' globals enter the global symbol table, so the program's
     * second pass resolves against them; a member can use the program's own globals in turn.
     * Returns no statements when no member defines any of the names.
     */
    public ArrayList<ProgramStatement> linkAfter(Set<String> undefined, int textEnd, int dataEnd) throws AssemblyException {
        legacyGlobals = true;
        resolveMembers(undefined);
        if (units.isEmpty()) return new ArrayList<>();
        return link(Integer.toUnsignedLong(textEnd), Integer.toUnsignedLong(dataEnd));
    }

    /**
     * Pulls library members until every strong global the units use is defined or no member
     * defines it, in rounds, so the link order is the order members were first needed.
     */
    private void resolveMembers(Set<String> required) throws AssemblyException {
        if (library == null) return;
        Set<String> loaded = new HashSet<>();
        for (Unit unit : units) loaded.add(unit.program.getFilename());
        boolean pulled = true;
        while (pulled) {
            pulled = false;
            Set<String> defined = new HashSet<>();
            Set<String> wanted = new LinkedHashSet<>(required);
            for (Unit unit : units) {
                defined.addAll(unit.definedGlobals());
                wanted.addAll(unit.strongReferences());
                // A weak reference pulls nothing, except to what the library says it always
                // supplies: GCC refers to __cxa_pure_virtual weakly from every abstract class's vtable.
                for (String name : unit.weakReferences()) if (library.resolvesWeak(name)) wanted.add(name);
            }
            for (String name : wanted) {
                if (defined.contains(name)) continue;
                String path = library.memberFor(name);
                if (path == null || !loaded.add(path)) continue;
                try {
                    Unit unit = new Unit(library.load(path), units.size());
                    units.add(unit);
                    unit.parseAll();
                    pulled = true;
                } catch (AssemblyException exception) {
                    for (ErrorMessage message : exception.errors().getErrorMessages()) errors.add(message);
                }
            }
            fail();
        }
    }

    private ArrayList<ProgramStatement> link(long textBase, long dataBase) throws AssemblyException {
        // GNU as relaxes a conditional branch whose target is out of its +-4 KiB range into the
        // inverted branch over a jal, and compilers rely on it. A branch can only be found to be far
        // once the program is laid out, and making it longer moves everything after it, alignment
        // included, so the units with a far branch are parsed again with it in its long form and
        // everything is laid out again, until no branch is far. Layout only places sections, so a
        // unit without a new far branch keeps its parse. Nothing has been written to memory or to
        // a symbol table before this loop ends.
        for (int round = 0; ; round++) {
            globals.clear();
            textGaps.clear();
            arrayRanges.clear();
            Set<String> suppliedGroups = new HashSet<>();
            for (Unit unit : units) {
                Set<String> groups = new HashSet<>();
                for (Section section : unit.sections.values()) {
                    if (section.group == null) continue;
                    section.duplicateGroup = suppliedGroups.contains(section.group);
                    groups.add(section.group);
                }
                suppliedGroups.addAll(groups);
            }
            for (Unit unit : units) unit.exportGlobals();
            if (entrySymbol != null && !globals.containsKey(entrySymbol))
                errors.add(new ErrorMessage(units.get(0).program, 1, 1, "Undefined entry symbol: " + entrySymbol));
            fail();
            attempt(units.get(0), 1, () -> layout(textBase, dataBase));
            fail();
            List<Unit> relaxed = new ArrayList<>();
            for (Unit unit : units) if (unit.relaxFarBranches()) relaxed.add(unit);
            if (relaxed.isEmpty() || round >= 16) break;
            for (Unit unit : relaxed) {
                Unit next = new Unit(unit.program, unit.index);
                next.longBranches.addAll(unit.longBranches);
                next.parseAll();
                units.set(units.indexOf(unit), next);
            }
            fail();
        }
        for (Unit unit : units) unit.bindSymbols();
        for (Map.Entry<String, Symbol> global : globals.entrySet()) {
            Symbol symbol = global.getValue();
            symbol.unit.attempt(symbol.line, () -> {
                Value value = symbol.unit.valueOf(symbol, new HashSet<>());
                if (value.number.signum() >= 0 && value.number.bitLength() <= 32) {
                    Token token = new Token(TokenTypes.IDENTIFIER, symbol.name, symbol.unit.program, symbol.line, 1);
                    Globals.symbolTable.addSymbol(token, value.number.intValue(), value.section != null && !value.section.executable(), errors);
                }
            });
        }
        // Bind all highs before lows; separated pairs and multiple low users are legal.
        for (Unit unit : units) unit.bindHighs();
        fail();
        ArrayList<ProgramStatement> machine = new ArrayList<>();
        for (Fragment gap : textGaps) gap.section.unit.attempt(gap.line, () -> gap.section.unit.emit(gap, machine));
        for (Unit unit : units) unit.emitAll(machine);
        fail();
        machine.sort(Comparator.comparingInt(ProgramStatement::getAddress));
        for (ProgramStatement statement : machine) statement.getSourceProgram().getParsedList().add(statement);
        SystemIO.resetFiles();
        return machine;
    }
    private void attempt(Unit unit, int line, Runnable action) { unit.attempt(line, action); }
    private void fail() throws AssemblyException { if (errors.errorsOccurred()) throw new AssemblyException(errors); }
    private static void require(boolean condition, String message) { if (!condition) throw new Invalid(message); }
    private static boolean matches(Pattern pattern, String text) { return pattern.matcher(text).matches(); }
    /** The array family whose bound ld provides under this name when nothing defines it, or -1. */
    private static int arrayBound(String name) {
        switch (name) {
            case "__init_array_start": case "__init_array_end": return INIT;
            case "__fini_array_start": case "__fini_array_end": return FINI;
            default: return -1;
        }
    }

    /** Places each family's sections in unit order, then each unit's sections in order of first appearance. */
    private void layout(long textBase, long dataBase) {
        long text = textBase, data = dataBase;
        long total = 0;
        for (int family = TEXT; family <= COMMON; family++) {
            Section range = null;
            if (family == INIT || family == FINI) {
                // The bounds start after the first member's alignment, as ld places them.
                range = new Section(units.get(0), family == INIT ? ".init_array" : ".fini_array", "aw", "@progbits", null, null, family);
                arrayRanges.put(family, range);
            }
            List<Section> placed = new ArrayList<>();
            for (Unit unit : units) for (Section section : unit.sections.values())
                if (section.family == family && !section.discarded()) placed.add(section);
            // A stable sort: equal priorities keep link order.
            if (range != null) placed.sort(Comparator.comparingInt(section -> section.priority));
            for (Section section : placed) {
                long position = family == TEXT ? text : data;
                position = (position + section.alignment - 1) & -(long)section.alignment;
                section.base = position;
                if (range != null && range.size == 0 && range.base == 0) range.base = position;
                if (family == TEXT && position > text) {
                    require(position - text <= MAX_BYTES, "Text section gap exceeds emission limit");
                    int line = section.fragments.isEmpty() ? 1 : section.fragments.get(0).line;
                    textGaps.add(new Fragment(section, line, (int)(text - position), (int)(position - text), null, null, 0, null, 0));
                    total += position - text;
                }
                long end = position + section.size;
                if (family == TEXT) require(end <= 0xffffffffL && (section.size == 0 || Memory.inTextSegment((int)(end - 1))), "Section exceeds mapped memory: " + section.name);
                else if (section.size != 0) requireStaticDataFits(end);
                if (family == TEXT) text = end; else data = end;
                total += section.size;
            }
            if (range != null) {
                if (range.base == 0) range.base = data;
                range.size = (int)(data - range.base);
            }
        }
        require(total <= MAX_BYTES, "Assembly exceeds 16 MiB emission limit");
        long heapBase = Integer.toUnsignedLong(Memory.heapBaseAddress);
        if (legacyGlobals) {
            // Members linked after a RARS-dialect program keep RARS's layout, whose heap starts at
            // its base whatever lies below it, so their data must end before it.
            require(data <= heapBase, "Static data reaches the heap at 0x" + Integer.toHexString(Memory.heapBaseAddress));
            heapStart = heapBase;
        } else {
            heapStart = data <= heapBase ? heapBase : (data + PAGE_BYTES - 1) & -PAGE_BYTES;
        }
    }

    /**
     * Static data and the heap after it share the data segment, which ends below the stack and
     * memory-mapped IO: 4128768 bytes from 0x10010000 to 0x10400000 in RARS's memory layout.
     */
    private static void requireStaticDataFits(long end) {
        long limit = Integer.toUnsignedLong(Memory.dataSegmentLimitAddress);
        String where = "the end of the data segment";
        if (Integer.toUnsignedLong(Memory.stackLimitAddress) + 1 < limit) {
            limit = Integer.toUnsignedLong(Memory.stackLimitAddress) + 1;
            where = "the stack";
        }
        if (Integer.toUnsignedLong(Memory.memoryMapBaseAddress) < limit) {
            limit = Integer.toUnsignedLong(Memory.memoryMapBaseAddress);
            where = "memory-mapped IO";
        }
        long base = Integer.toUnsignedLong(Memory.dataBaseAddress);
        require(end <= limit, "Static data ends at 0x" + Long.toHexString(end) + ", past " + where + " at 0x"
                + Long.toHexString(limit) + ": .data, .rodata, .bss and common symbols together fit in "
                + (limit - base) + " bytes from 0x" + Long.toHexString(base));
    }

    /** Where a RARS-dialect program's global lives, for the checks a section supplies: text or data. */
    private Section legacySection(int address) {
        if (Memory.inTextSegment(address)) {
            if (legacyText == null) legacyText = new Section(null, "(program text)", "ax", "@progbits", null, null, TEXT);
            return legacyText;
        }
        if (legacyData == null) legacyData = new Section(null, "(program data)", "aw", "@progbits", null, null, DATA);
        return legacyData;
    }

    /** One translation unit: its own sections, local symbols, numeric labels and directives state. */
    private final class Unit {
        final RISCVprogram program;
        final int index;
        final LinkedHashMap<String, Section> sections = new LinkedHashMap<>();
        /** The most recently declared section of each name, for re-entry without flags. */
        final Map<String, Section> sectionsByName = new HashMap<>();
        final LinkedHashMap<String, Symbol> symbols = new LinkedHashMap<>();
        final Map<String, Symbol> forwardAliases = new HashMap<>();
        final Map<String, List<Symbol>> numeric = new HashMap<>();
        final Set<String> declared = new LinkedHashSet<>();
        final Set<String> weak = new HashSet<>();
        final Set<String> definedCommon = new HashSet<>();
        /** Lines whose conditional branch a previous layout found out of range, emitted long. */
        final Set<Integer> longBranches = new HashSet<>();
        final Deque<Section> sectionStack = new ArrayDeque<>();
        int optionDepth;
        Section current, previous;

        Unit(RISCVprogram program, int index) { this.program = program; this.index = index; }

        void attempt(int line, Runnable action) {
            if (errors.errorLimitExceeded()) return;
            try { action.run(); }
            catch (Invalid | IllegalArgumentException exception) { errors.add(new ErrorMessage(program, line, 1, exception.getMessage())); }
        }

        void parseAll() {
            program.createParsedList();
            program.createMacroPool();
            current = section(".text", null, null, null, null);
            // Absolute aliases may be declared after a layout count or li. Their actual
            // section/location is bound in parse(); this index is used only in absolute contexts.
            for (int i = 0; i < program.getSourceList().size(); i++) {
                String text = uncomment(program.getSourceList().get(i)).trim();
                Matcher assignment = ASSIGNMENT.matcher(text);
                Matcher directive = SET_DIRECTIVE.matcher(text);
                Matcher match = assignment.matches() ? assignment : directive.matches() ? directive : null;
                if (match != null) forwardAliases.putIfAbsent(match.group(1), new Symbol(this, match.group(1), null, 0, i + 1, match.group(2)));
            }
            for (int i = 0; i < program.getSourceList().size(); i++) {
                final int line = i + 1;
                attempt(line, () -> parse(uncomment(program.getSourceList().get(line - 1)).trim(), line));
            }
        }

        /** The global and weak names this unit defines. */
        Set<String> definedGlobals() {
            Set<String> names = new LinkedHashSet<>();
            for (String name : declared) {
                Symbol symbol = symbols.get(name);
                if (symbol != null && (symbol.section == null || symbol.section.family >= 0)) names.add(name);
            }
            return names;
        }

        /**
         * The names this unit uses without defining them, other than weak references, which ld
         * never pulls an archive member for. A name declared global and not defined counts as used.
         */
        Set<String> strongReferences() {
            Set<String> names = referencedNames();
            names.removeIf(name -> symbols.containsKey(name) || weak.contains(name));
            return names;
        }

        /** The names this unit refers to weakly without defining them, which ld never pulls a member for. */
        Set<String> weakReferences() {
            Set<String> names = referencedNames();
            names.removeIf(name -> symbols.containsKey(name) || !weak.contains(name));
            return names;
        }

        private Set<String> referencedNames() {
            Set<String> names = new LinkedHashSet<>();
            for (Section section : sections.values()) {
                if (section.family < 0) continue;
                for (Fragment fragment : section.fragments) {
                    if (fragment.instruction != null) {
                        String instruction = fragment.instruction;
                        int space = instruction.indexOf(' ');
                        String op = space < 0 ? instruction : instruction.substring(0, space);
                        // fence's operands are access sets (iorw), not symbols.
                        if (space >= 0 && !op.startsWith("fence")) collectNames(instruction.substring(space + 1), op, names);
                    }
                    if (fragment.expressions != null) for (String expression : fragment.expressions) collectNames(expression, null, names);
                }
            }
            for (Symbol symbol : symbols.values())
                if (symbol.alias != null && (symbol.section == null || symbol.section.family >= 0)) collectNames(symbol.alias, null, names);
            names.addAll(declared);
            return names;
        }
        /**
         * The symbols {@code text} names: an instruction's operands when {@code op} is its
         * mnemonic, or an expression when it is null. Numbers, numeric labels, address modifiers and
         * the location counter never are. Register names are not symbols among operands, and CSR and
         * rounding-mode names only where the instruction takes one, so calling a function named
         * time or cycle still pulls its member.
         */
        private void collectNames(String text, String op, Set<String> names) {
            boolean csr = op != null && op.startsWith("csr"), rounding = op != null && op.startsWith("f");
            Matcher matcher = REFERENCE.matcher(text);
            while (matcher.find()) {
                String name = matcher.group();
                if (name.charAt(0) == '%' || Character.isDigit(name.charAt(0)) || name.equals(".")) continue;
                if (op != null && (RegisterFile.getRegister(name) != null || FloatingPointRegisterFile.getRegister(name) != null)) continue;
                if (csr && ControlAndStatusRegisterFile.getRegister(name) != null || rounding && matches(ROUNDING_MODE, name)) continue;
                names.add(name);
            }
        }

        /**
         * Marks the lines whose conditional branch the current layout puts out of range, and says
         * whether any were new. A target that does not resolve is left for emission to report.
         */
        boolean relaxFarBranches() {
            boolean found = false;
            for (Section section : sections.values()) {
                if (!section.executable() || section.discarded()) continue;
                for (Fragment fragment : section.fragments) {
                    if (fragment.instruction == null || longBranches.contains(fragment.line)) continue;
                    Matcher branch = CONDITIONAL_BRANCH.matcher(fragment.instruction);
                    if (!branch.matches()) continue;
                    try {
                        Value target = evaluate(branch.group(3), fragment.line, section, fragment.offset, false);
                        long delta = target.number.longValue() - fragment.address();
                        if (delta < -4096 || delta > 4094) {
                            longBranches.add(fragment.line);
                            found = true;
                        }
                    } catch (Invalid | IllegalArgumentException ignored) {
                        // Reported, with its line, when the instruction is emitted.
                    }
                }
            }
            return found;
        }

        /** Offers this unit's global and weak definitions to the other units. */
        void exportGlobals() {
            for (String name : declared) {
                Symbol symbol = symbols.get(name);
                if (symbol == null || symbol.section != null && symbol.section.discarded()) continue;
                attempt(symbol.line, () -> {
                    Symbol existing = globals.get(name);
                    boolean strong = !weak.contains(name);
                    if (existing == null || strong && existing.unit.weak.contains(name)) { globals.put(name, symbol); return; }
                    require(!strong || existing.unit.weak.contains(name), "Multiple definition of " + name + ", first defined in " + existing.unit.program.getFilename());
                });
            }
        }

        /** Records this unit's symbols in its local table and checks that its non-weak declarations resolve. */
        void bindSymbols() {
            for (Symbol symbol : symbols.values()) attempt(symbol.line, () -> {
                if (symbol.section != null && symbol.section.discarded()) return;
                // A weak definition another unit overrides is not this name's address.
                if (declared.contains(symbol.name) && globals.get(symbol.name) != symbol) return;
                Value value = resolve(symbol.name, symbol.line, symbol.section, symbol.offset, false, new HashSet<>());
                if (value.number.signum() >= 0 && value.number.bitLength() <= 32) {
                    Token token = new Token(TokenTypes.IDENTIFIER, symbol.name, program, symbol.line, 1);
                    program.getLocalSymbolTable().addSymbol(token, value.number.intValue(), value.section != null && !value.section.executable(), errors);
                }
            });
            for (String name : declared) if (!weak.contains(name)) attempt(1, () -> resolve(name, 1, current, 0, false, new HashSet<>()));
        }

        void bindHighs() {
            for (Section section : sections.values()) {
                if (section.discarded()) continue;
                for (Fragment fragment : section.fragments) {
                    if (fragment.instruction == null || !fragment.instruction.contains("%pcrel_hi(")) continue;
                    attempt(fragment.line, () -> {
                        require(fragment.instruction.startsWith("auipc "), "%pcrel_hi requires auipc");
                        String expression = modifierExpression(fragment.instruction, fragment.instruction.indexOf("%pcrel_hi("));
                        highs.put(fragment.address(), new HighFixup(fragment, expression));
                    });
                }
            }
        }

        void emitAll(ArrayList<ProgramStatement> machine) {
            for (Section section : sections.values()) {
                if (section.discarded()) continue;
                for (Fragment fragment : section.fragments) attempt(fragment.line, () -> emit(fragment, machine));
            }
        }

        private void parse(String text, int line) {
            require(!hasStatementSeparator(text), "Multiple GNU statements on one line are unsupported");
            Matcher label = LABEL.matcher(text);
            while (label.find()) {
                String name = label.group(1);
                Symbol symbol = new Symbol(this, name, current, current.size, line, null);
                if (matches(DIGITS, name)) {
                    numeric.computeIfAbsent(name, key -> new ArrayList<>()).add(symbol);
                    symbol = new Symbol(this, "__gnu_numeric_" + line + "_" + name, current, current.size, line, null);
                }
                define(symbol);
                text = text.substring(label.end()).trim();
                label = LABEL.matcher(text);
            }
            if (text.isEmpty()) return;
            Matcher assignment = ASSIGNMENT.matcher(text);
            if (assignment.matches()) {
                if (!current.discarded()) define(new Symbol(this, assignment.group(1), current, current.size, line, assignment.group(2)));
                return;
            }
            int split = text.indexOf(' '), tab = text.indexOf('\t');
            if (split < 0 || tab >= 0 && tab < split) split = tab;
            String op = split < 0 ? text : text.substring(0, split);
            String rest = split < 0 ? "" : text.substring(split).trim();
            List<String> args = arguments(rest);
            if (op.equals(".include")) return;
            if (op.equals(".text") || op.equals(".data") || op.equals(".bss") || op.equals(".rodata")) {
                require(args.isEmpty(), "Section addresses/subsections are unsupported");
                switchSection(section(op, null, null, null, null)); return;
            }
            if (op.equals(".section") || op.equals(".pushsection")) {
                Section next = sectionDirective(args);
                if (op.equals(".pushsection")) sectionStack.push(current);
                switchSection(next); return;
            }
            if (op.equals(".popsection") || op.equals(".previous")) {
                require(args.isEmpty(), "Unexpected section operands");
                require(op.equals(".previous") ? previous != null : !sectionStack.isEmpty(), "Section stack underflow");
                switchSection(op.equals(".previous") ? previous : sectionStack.pop()); return;
            }
            // Only audited nonallocatable families can be discarded. Their labels are
            // retained so references from runtime sections produce a specific error.
            if (current.family < 0 && !op.equals(".option") && !op.equals(".attribute")) return;
            if (op.equals(".option")) { option(args); return; }
            if (op.equals(".attribute")) { attribute(args); return; }
            if (op.equals(".set") || op.equals(".equ")) {
                require(args.size() == 2 && matches(NAME, args.get(0)), "Invalid alias definition");
                define(new Symbol(this, args.get(0), current, current.size, line, args.get(1))); return;
            }
            if (op.equals(".globl") || op.equals(".global") || op.equals(".weak")) {
                require(!args.isEmpty(), "Missing symbol declaration");
                for (String name : args) {
                    require(matches(NAME, name), "Invalid symbol name");
                    declared.add(name);
                    if (op.equals(".weak")) weak.add(name);
                }
                return;
            }
            if (op.equals(".local") || op.equals(".hidden") || op.equals(".protected") || op.equals(".internal")) {
                require(!args.isEmpty(), "Missing symbol declaration");
                for (String name : args) require(matches(NAME, name), "Invalid symbol name");
                return;
            }
            if (op.equals(".type")) {
                require(args.size() == 2 && matches(SYMBOL_TYPE, args.get(1)), "Unsupported symbol type"); return;
            }
            if (op.equals(".size")) {
                require(args.size() == 2, "Invalid symbol size");
                // Evaluate after layout, just like data fixups, without emitting bytes.
                append(line, 0, null, Collections.singletonList(args.get(1)), 0, null, 0); return;
            }
            if (op.equals(".file") || op.equals(".loc") || op.equals(".ident") || op.equals(".addrsig") || op.equals(".addrsig_sym") ||
                    matches(IGNORED_CFI, op)) return;
            if (op.equals(".comm") || op.equals(".lcomm")) {
                require(args.size() >= 2 && args.size() <= 3, "Invalid common allocation");
                require(matches(NAME, args.get(0)), "Invalid common symbol name");
                require(definedCommon.add(args.get(0)), "Competing common definitions: " + args.get(0));
                int size = count(args.get(1), line), align = args.size() > 2 ? count(args.get(2), line) : Math.max(1, Integer.highestOneBit(Math.min(size, 16)));
                require(align > 0 && (align & (align - 1)) == 0, "Common alignment must be a power of two");
                Section saved = current;
                current = section(".common", "aw", "@nobits", null, null);
                align(align, 0, MAX_BYTES, line);
                define(new Symbol(this, args.get(0), current, current.size, line, null));
                append(line, size, null, null, 0, null, 0);
                current = saved; return;
            }
            if (op.equals(".align") || op.equals(".p2align") || op.equals(".balign")) {
                require(args.size() >= 1 && args.size() <= 3, "Invalid alignment operands");
                int alignment = count(args.get(0), line);
                if (!op.equals(".balign")) { require(alignment <= 24, "Alignment exponent exceeds limit"); alignment = 1 << alignment; }
                require(alignment > 0, "Alignment must be positive");
                int fill = args.size() > 1 && !args.get(1).isEmpty() ? count(args.get(1), line) : -1;
                require(fill <= 255, "Alignment fill must be a byte");
                int max = args.size() > 2 ? count(args.get(2), line) : MAX_BYTES;
                align(alignment, fill, max, line); return;
            }
            if (op.equals(".zero") || op.equals(".space") || op.equals(".skip")) {
                require(args.size() >= 1 && args.size() <= 2 && !current.executable(), "Invalid data allocation");
                int fill = args.size() > 1 ? count(args.get(1), line) : 0;
                require(fill <= 255 && (!current.zeroed() || fill == 0), "Invalid fill in zeroed section");
                append(line, count(args.get(0), line), null, null, 0, null, fill); return;
            }
            if (op.equals(".ascii") || op.equals(".asciz") || op.equals(".string")) {
                require(!current.executable() && !args.isEmpty(), "Strings require a data section");
                ByteArrayOutputStream bytes = new ByteArrayOutputStream();
                for (String arg : args) {
                    byte[] payload = stringBytes(arg);
                    bytes.write(payload, 0, payload.length);
                    if (!op.equals(".ascii")) bytes.write(0);
                }
                byte[] payload = bytes.toByteArray();
                require(!current.zeroed() || allZero(payload), "Nonzero data in zeroed section");
                append(line, payload.length, null, null, 0, payload, 0); return;
            }
            if (op.equals(".float") || op.equals(".double")) {
                require(!args.isEmpty() && !current.executable() && !current.zeroed(), "Invalid floating data");
                int width = op.equals(".float") ? 4 : 8;
                ByteArrayOutputStream bytes = new ByteArrayOutputStream();
                for (String arg : args) {
                    long bits;
                    // Correctly rounded straight to the width, as GNU as does.
                    try { bits = width == 4 ? Float.floatToRawIntBits(JavaNumberText.parseFloat(arg)) & 0xffffffffL : Double.doubleToRawLongBits(JavaNumberText.parseDouble(arg)); }
                    catch (NumberFormatException exception) { throw new Invalid("Invalid floating literal: " + arg); }
                    for (int i = 0; i < width; i++) bytes.write((int)(bits >>> (8 * i)) & 255);
                }
                append(line, bytes.size(), null, null, 0, bytes.toByteArray(), 0); return;
            }
            int width = integerWidth(op);
            if (width != 0) {
                require(!args.isEmpty() && !current.executable(), "Integer data requires a data section");
                append(line, Math.multiplyExact(width, args.size()), null, args, width, null, 0); return;
            }
            require(!op.startsWith("."), "Unsupported GNU directive: " + op);
            require(current.executable(), "Instruction outside executable section");
            require(current.size % 4 == 0, "Instruction address is not four-byte aligned");
            for (String instruction : lower(op, args, line)) {
                Matcher branch = CONDITIONAL_BRANCH.matcher(instruction);
                if (longBranches.contains(line) && branch.matches()) {
                    // b<inverse> rs1, rs2, past; jal zero, target; past:
                    String past = "__gnu_relax_" + line;
                    define(new Symbol(this, past, current, current.size + 8, line, null));
                    append(line, 4, INVERSE_BRANCH.get(branch.group(1)) + " " + branch.group(2) + "," + past, null, 0, null, 0);
                    append(line, 4, "jal zero," + branch.group(3), null, 0, null, 0);
                } else append(line, 4, instruction, null, 0, null, 0);
            }
        }
        private void define(Symbol symbol) {
            require(!symbols.containsKey(symbol.name), "Duplicate/reassigned symbol: " + symbol.name);
            symbols.put(symbol.name, symbol);
        }
        private void switchSection(Section next) { previous = current; current = next; }
        /** `.section name[,"flags"[,@type[,entry size][,group,comdat]]]` */
        private Section sectionDirective(List<String> args) {
            require(!args.isEmpty(), "Unsupported section declaration");
            String name = unquoteOptional(args.get(0));
            String flags = args.size() > 1 ? unquote(args.get(1)) : null;
            String type = args.size() > 2 ? args.get(2) : null;
            int next = 3;
            String entrySize = null, group = null;
            if (flags != null && flags.contains("M") && args.size() > next) entrySize = args.get(next++);
            if (flags != null && flags.contains("G")) {
                require(args.size() > next, "Group section flags require a group name: " + name);
                group = args.get(next++);
                require(matches(NAME, group), "Invalid section group name: " + group);
                // Without "comdat" a group is never deduplicated, which no supported compiler emits.
                require(args.size() > next && args.get(next++).equals("comdat"), "Only COMDAT section groups are supported: " + name);
            }
            require(args.size() <= next, "Unsupported section declaration");
            if (flags == null) {
                Section known = sectionsByName.get(name);
                if (known != null) return known;
            }
            return section(name, flags, type, entrySize, group);
        }
        private Section section(String name, String flags, String type, String entrySize, String group) {
            int family = matches(TEXT_SECTION, name) ? TEXT : matches(RODATA_SECTION, name) ? RODATA :
                    name.equals(".init_array") || name.startsWith(".init_array.") && matches(PRIORITIZED_ARRAY, name) ? INIT :
                    name.equals(".fini_array") || name.startsWith(".fini_array.") && matches(PRIORITIZED_ARRAY, name) ? FINI :
                    matches(DATA_SECTION, name) ? DATA : matches(BSS_SECTION, name) ? BSS : name.equals(".common") ? COMMON : -1;
            boolean debug = matches(DEBUG_SECTION, name);
            require(family >= 0 || debug, "Unsupported section: " + name);
            boolean array = family == INIT || family == FINI;
            String actualFlags = flags == null ? family == TEXT ? "ax" : family == RODATA ? "a" : family >= INIT ? "aw" : "" : flags;
            String actualType = type == null ? family >= BSS ? "@nobits" : "@progbits" : type.replace('%', '@');
            if (array && actualType.equals(family == INIT ? "@init_array" : "@fini_array")) actualType = "@progbits";
            require(matches(SECTION_FLAGS, actualFlags) && (actualType.equals("@progbits") || actualType.equals("@nobits")), "Unsupported section flags/type: " + name);
            require(family >= 0 ? actualFlags.contains("a") && actualFlags.contains("x") == (family == TEXT) && actualFlags.contains("w") == (family >= INIT) : !actualFlags.contains("a"), "Conflicting section flags: " + name);
            require(actualType.equals(family >= BSS ? "@nobits" : "@progbits"), "Conflicting section type: " + name);
            require(group == null || family >= 0, "Section group on a discarded section: " + name);
            if (actualFlags.contains("M")) require(entrySize != null && matches(ENTRY_SIZE, entrySize) && Integer.parseInt(entrySize) <= 256, "Mergeable section requires a bounded entry size");
            else require(entrySize == null, "Unexpected section entry size");
            require(!actualFlags.contains("S") || actualFlags.contains("M"), "String section requires merge flag");
            String key = group == null ? name : name + "\0" + group;
            Section found = sections.get(key);
            if (found != null) {
                if (flags != null) require(found.flags.equals(actualFlags) && found.type.equals(actualType) && Objects.equals(found.entrySize, entrySize), "Conflicting repeated section declaration: " + name);
                return found;
            }
            Section created = new Section(this, name, actualFlags, actualType, entrySize, group, family);
            Matcher prioritized = PRIORITIZED_ARRAY.matcher(name);
            if (prioritized.matches()) created.priority = Integer.parseInt(prioritized.group(1));
            sections.put(key, created);
            sectionsByName.put(name, created);
            return created;
        }
        private void append(int line, int size, String instruction, List<String> expressions, int width, byte[] bytes, int fill) {
            require(size >= 0 && size <= MAX_BYTES - current.size, "Section exceeds 16 MiB limit");
            current.fragments.add(new Fragment(current, line, current.size, size, instruction, expressions, width, bytes, fill));
            current.size += size;
        }
        private void align(int alignment, int fill, int max, int line) {
            require((alignment & (alignment - 1)) == 0, "Alignment must be a power of two");
            current.alignment = Math.max(current.alignment, alignment);
            int skip = (alignment - current.size % alignment) % alignment;
            if (skip > max) return;
            require(!current.executable() || skip % 4 == 0, "Executable padding must contain whole instruction words");
            require(!current.zeroed() || fill <= 0, "Nonzero padding in zeroed section");
            append(line, skip, null, null, 0, null, fill < 0 ? current.executable() ? -1 : 0 : fill);
        }
        private int count(String expression, int line) {
            Value value = evaluate(expression, line, current, current.size, true);
            require(value.number.signum() >= 0 && value.number.compareTo(BigInteger.valueOf(MAX_BYTES)) <= 0, "Layout value is out of range");
            return value.number.intValue();
        }
        private void option(List<String> args) {
            require(args.size() == 1, "Invalid .option");
            switch (args.get(0)) {
                case "push": optionDepth++; break;
                case "pop": require(optionDepth > 0, "Option stack underflow"); optionDepth--; break;
                case "nopic": case "norvc": case "norelax": case "relax": break;
                default: throw new Invalid("Unsupported GNU option: " + args.get(0));
            }
        }
        private void attribute(List<String> args) {
            require(args.size() == 2, "Invalid architecture attribute");
            String key = args.get(0), value = args.get(1);
            if (key.equals("stack_align") || key.equals("4")) require(value.equals("16"), "Unsupported ABI stack alignment");
            else if (key.equals("unaligned_access") || key.equals("6")) require(value.equals("0"), "Unaligned instruction access is unsupported");
            else if (key.equals("arch") || key.equals("5")) {
                String arch = unquote(value);
                String base = Globals.is64Bit() ? "rv64i" : "rv32i";
                require(arch.startsWith(base), "Architecture width does not match Core");
                String extensions = arch.substring(base.length());
                require(matches(ARCH_EXTENSIONS, extensions) &&
                        (!extensions.contains("_zmmul") || extensions.contains("_m")), "Unsupported architecture attribute: " + arch);
            } else throw new Invalid("Unsupported architecture attribute: " + key);
        }

        private List<String> lower(String op, List<String> args, int line) {
            require(!matches(ATOMIC, op), "Atomic instructions are outside GNU compiler v1");
            String joined = String.join(",", args);
            if (op.equals("li")) {
                require(args.size() == 2, "li requires register and constant");
                BigInteger value = evaluate(args.get(1), line, current, current.size, true).number;
                int bits = Globals.is64Bit() ? 64 : 32;
                require(value.bitLength() <= bits && value.compareTo(ONE.shiftLeft(bits - 1).negate()) >= 0, "li value exceeds register width");
                if (value.testBit(bits - 1) && value.signum() >= 0) value = value.subtract(ONE.shiftLeft(bits));
                List<String> result = new ArrayList<>(); materialize(args.get(0), value, result); return result;
            }
            if (op.equals("la") || op.equals("lla") || op.equals("call") || op.equals("tail")) {
                boolean call = op.equals("call"), tail = op.equals("tail");
                require(args.size() == (call || tail ? 1 : 2), "Unsupported address pseudo operands");
                String rd = call ? "ra" : tail ? "t1" : args.get(0), target = args.get(args.size() - 1);
                String anchor = "__gnu_anchor_" + line + "_" + current.size;
                define(new Symbol(this, anchor, current, current.size, line, null));
                return Arrays.asList("auipc " + rd + ",%pcrel_hi(" + target + ")", (call || tail ? "jalr " + (call ? "ra" : "zero") + "," + rd + "," : "addi " + rd + "," + rd + ",") + "%pcrel_lo(" + anchor + ")");
            }
            if (matches(MEMORY_ACCESS, op) && args.size() >= 2 && !args.get(1).endsWith(")")) {
                boolean integerLoad = op.charAt(0) == 'l';
                require(args.size() == (integerLoad ? 2 : 3), "Address memory pseudo requires an explicit scratch register");
                String scratch = integerLoad ? args.get(0) : args.get(2);
                require(!scratch.equals("zero") && !scratch.equals("x0"), "Address pseudo cannot use zero as scratch");
                String anchor = "__gnu_anchor_" + line + "_" + current.size;
                define(new Symbol(this, anchor, current, current.size, line, null));
                return Arrays.asList("auipc " + scratch + ",%pcrel_hi(" + args.get(1) + ")", op + " " + args.get(0) + ",%pcrel_lo(" + anchor + ")(" + scratch + ")");
            }
            switch (op) {
                case "nop": require(args.isEmpty(), "nop takes no operands"); return List.of("addi zero,zero,0");
                // Clang marks unreachable code with it. GNU as encodes it, without the C extension,
                // as a write to the read-only cycle CSR, which raises an illegal instruction.
                case "unimp": require(args.isEmpty(), "unimp takes no operands"); return List.of("csrrw zero,cycle,zero");
                case "mv": require(args.size() == 2, "mv requires two operands"); return List.of("addi " + joined + ",0");
                case "ret": require(args.isEmpty(), "ret takes no operands"); return List.of("jalr zero,ra,0");
                case "jr": require(args.size() == 1, "jr requires one operand"); return List.of("jalr zero," + joined + ",0");
                case "j": require(args.size() == 1, "j requires one operand"); return List.of("jal zero," + joined);
                case "jal": if (args.size() == 1) return List.of("jal ra," + joined); break;
                case "jalr":
                    if (args.size() == 1) return List.of("jalr ra," + joined + ",0");
                    if (args.size() == 2 && args.get(1).endsWith(")")) {
                        int paren = args.get(1).lastIndexOf('(');
                        return List.of("jalr " + args.get(0) + "," + args.get(1).substring(paren + 1, args.get(1).length() - 1) + "," + args.get(1).substring(0, paren));
                    } break;
                case "neg": case "negw": require(args.size() == 2, "neg requires two operands"); return List.of((op.equals("negw") ? "subw " : "sub ") + args.get(0) + ",zero," + args.get(1));
                case "not": require(args.size() == 2, "not requires two operands"); return List.of("xori " + joined + ",-1");
                case "sext.w": require(args.size() == 2, "sext.w requires two operands"); return List.of("addiw " + joined + ",0");
                case "seqz": require(args.size() == 2, "seqz requires two operands"); return List.of("sltiu " + joined + ",1");
                case "snez": require(args.size() == 2, "snez requires two operands"); return List.of("sltu " + args.get(0) + ",zero," + args.get(1));
                case "sltz": require(args.size() == 2, "sltz requires two operands"); return List.of("slt " + joined + ",zero");
                case "sgtz": require(args.size() == 2, "sgtz requires two operands"); return List.of("slt " + args.get(0) + ",zero," + args.get(1));
                case "beqz": case "bnez": case "blez": case "bgez": case "bltz": case "bgtz":
                    require(args.size() == 2, "Invalid zero branch operands");
                    String branch = op.equals("beqz") ? "beq" : op.equals("bnez") ? "bne" : op.equals("bgez") || op.equals("blez") ? "bge" : "blt";
                    String regs = op.equals("blez") || op.equals("bgtz") ? "zero," + args.get(0) : args.get(0) + ",zero";
                    return List.of(branch + " " + regs + "," + args.get(1));
                case "bgt": case "ble": case "bgtu": case "bleu":
                    require(args.size() == 3, "Invalid branch operands");
                    return List.of((op.startsWith("bgt") ? "blt" : "bge") + (op.endsWith("u") ? "u" : "") + " " + args.get(1) + "," + args.get(0) + "," + args.get(2));
                default: break;
            }
            // A basic instruction's operand expressions are resolved after layout. The form is chosen
            // by operand count: GNU writes the floating point operations without their rounding mode
            // (fadd.d fs0,fa0,fa0), which is a pseudo-instruction here, beside a basic form with one.
            ArrayList<Instruction> matches = Globals.instructionSet.matchOperator(op);
            require(matches != null && !matches.isEmpty(), "Unsupported instruction: " + op);
            ArrayList<Instruction> pseudos = new ArrayList<>();
            boolean basicOfOtherArity = false;
            for (Instruction instruction : matches) {
                boolean arity = operandCount(instruction) == args.size();
                if (instruction instanceof BasicInstruction) {
                    if (arity) return List.of(op + " " + joined);
                    basicOfOtherArity = true;
                } else if (arity) pseudos.add(instruction);
            }
            // A basic instruction with no pseudo of this arity: encoding reports the operands.
            if (pseudos.isEmpty() && basicOfOtherArity) return List.of(op + " " + joined);
            // Register-only aliases use the existing fixed native templates.
            TokenList tokens = new Tokenizer(program).tokenizeLine(line, op + " " + joined, errors, false);
            Instruction instruction = OperandFormat.bestOperandMatch(tokens, pseudos.isEmpty() ? matches : pseudos);
            require(instruction instanceof ExtendedInstruction && OperandFormat.tokenOperandMatch(tokens, instruction, errors), "Unsupported pseudo instruction: " + op);
            List<String> result = new ArrayList<>();
            for (String template : ((ExtendedInstruction)instruction).getBasicIntructionTemplateList()) {
                require(!matches(ADDRESS_TEMPLATE, template), "Unsupported address pseudo: " + op);
                // Templates keep the spacing of their table, which a whitespace-led line would turn
                // into an empty operation.
                result.add(ExtendedInstruction.makeTemplateSubstitutions(program, template, tokens, 0).trim().replaceAll("\\s+", " "));
            }
            return result;
        }
        /** How many operands an instruction's example takes: `fadd.d f1, f2, f3, dyn` takes four. */
        private int operandCount(Instruction instruction) {
            String example = instruction.getExampleFormat().trim();
            int space = example.indexOf(' ');
            return space < 0 ? 0 : arguments(example.substring(space + 1)).size();
        }

        private void materialize(String rd, BigInteger value, List<String> result) {
            if (value.bitLength() <= 11) { result.add("addi " + rd + ",zero," + value); return; }
            BigInteger lo = signedLow(value), hi = value.subtract(lo).shiftRight(12);
            if (value.bitLength() <= 31) {
                result.add("lui " + rd + "," + hi.and(ONE.shiftLeft(20).subtract(ONE)));
                if (lo.signum() != 0) result.add((Globals.is64Bit() ? "addiw " : "addi ") + rd + "," + rd + "," + lo);
            } else {
                materialize(rd, hi, result);
                result.add("slli " + rd + "," + rd + ",12");
                if (lo.signum() != 0) result.add("addi " + rd + "," + rd + "," + lo);
            }
        }

        private void emit(Fragment fragment, ArrayList<ProgramStatement> machine) {
            if (fragment.instruction != null) {
                String text = resolveInstruction(fragment);
                encode(text, fragment, machine); return;
            }
            if (fragment.expressions != null) {
                for (int index = 0; index < fragment.expressions.size(); index++) {
                    Value value = evaluate(fragment.expressions.get(index), fragment.line, fragment.section, fragment.offset + index * fragment.width, false);
                    if (fragment.width == 0) { require(value.number.signum() >= 0, "Negative symbol size"); continue; }
                    int bits = fragment.width * 8;
                    boolean fits = value.number.compareTo(ONE.shiftLeft(bits - 1).negate()) >= 0 && value.number.compareTo(ONE.shiftLeft(bits)) < 0;
                    require(!value.symbolic || fits, "Symbol fixup overflows " + bits + " bits");
                    if (!fits) errors.add(new ErrorMessage(true, program, fragment.line, 1, "Integer literal truncated to " + bits + " bits"));
                    require(!fragment.section.zeroed() || value.number.signum() == 0, "Nonzero initializer in zeroed section");
                    for (int byteIndex = 0; byteIndex < fragment.width; byteIndex++) writeByte(fragment.address() + index * fragment.width + byteIndex, value.number.shiftRight(byteIndex * 8).intValue() & 255);
                }
                return;
            }
            if (fragment.section.executable()) {
                for (int i = 0; i < fragment.size; i += 4) {
                    Fragment word = new Fragment(fragment.section, fragment.line, fragment.offset + i, 4, null, null, 0, null, fragment.fill);
                    if (fragment.fill == -1) encode("addi zero,zero,0", word, machine);
                    else {
                        int binary = fragment.fill * 0x01010101;
                        ProgramStatement statement = ProgramStatement.rawPadding(binary, (int)word.address(), program, program.getSourceLineList().get(fragment.line - 1));
                        store(statement, machine);
                    }
                }
            } else for (int i = 0; i < fragment.size; i++) writeByte(fragment.address() + i, fragment.bytes == null ? fragment.fill : fragment.bytes[i] & 255);
        }
        private void writeByte(long address, int value) {
            try { Globals.memory.setByte((int)address, value); }
            catch (AddressErrorException exception) { throw new Invalid(exception.getMessage()); }
        }
        private String resolveInstruction(Fragment fragment) {
            String text = fragment.instruction;
            Matcher anyModifier = ANY_MODIFIER.matcher(text);
            while (anyModifier.find()) require(matches(SUPPORTED_MODIFIER, anyModifier.group(1)), "Unsupported address modifier: %" + anyModifier.group(1));
            Matcher matcher = MODIFIER.matcher(text);
            while (matcher.find()) {
                int start = matcher.start();
                String kind = matcher.group(1), expression = modifierExpression(text, start);
                int end = start + kind.length() + expression.length() + 3;
                BigInteger x;
                if (kind.equals("pcrel_lo")) {
                    require(matches(PCREL_LO_ANCHOR, expression.trim()), "PC-relative low requires an anchor with zero addend");
                    Value anchor = evaluate(expression, fragment.line, fragment.section, fragment.offset, false);
                    HighFixup high = highs.get(anchor.number.longValue());
                    require(high != null && high.fragment.section == fragment.section && anchor.section == fragment.section, "Missing or cross-section PC-relative high anchor: " + expression);
                    x = evaluate(high.expression, high.fragment.line, high.fragment.section, high.fragment.offset, false).number.subtract(BigInteger.valueOf(high.fragment.address()));
                } else {
                    x = evaluate(expression, fragment.line, fragment.section, fragment.offset, false).number;
                    if (kind.equals("pcrel_hi")) x = x.subtract(BigInteger.valueOf(fragment.address()));
                }
                BigInteger hi = x.add(BigInteger.valueOf(0x800)).shiftRight(12), lo = x.subtract(hi.shiftLeft(12));
                require(x.compareTo(BigInteger.valueOf(Integer.MIN_VALUE)) >= 0 && x.compareTo(BigInteger.valueOf(Integer.MAX_VALUE)) <= 0, "Address modifier exceeds signed 32-bit range");
                if (Globals.is64Bit()) require(hi.bitLength() <= 19, "Address modifier cannot be represented after RV64 sign extension");
                boolean high = kind.endsWith("hi");
                require(high ? text.startsWith(kind.equals("hi") ? "lui " : "auipc ") : !text.startsWith("lui ") && !text.startsWith("auipc "), "Address modifier used in wrong operand format");
                text = text.substring(0, start) + (high ? hi.and(ONE.shiftLeft(20).subtract(ONE)) : lo) + text.substring(end);
                matcher = MODIFIER.matcher(text);
            }
            text = text.trim();
            int space = 0;
            while (space < text.length() && !Character.isWhitespace(text.charAt(space))) space++;
            String op = text.substring(0, space), rest = space < text.length() ? text.substring(space + 1).trim() : "";
            List<String> args = arguments(rest);
            boolean branch = matches(BRANCH, op);
            if (branch) {
                int targetIndex = args.size() - 1;
                Value target = evaluate(args.get(targetIndex), fragment.line, fragment.section, fragment.offset, false);
                BigInteger delta = target.number.subtract(BigInteger.valueOf(fragment.address()));
                int bits = op.equals("jal") ? 21 : 13;
                require(target.section != null && target.section.executable() && target.number.and(BigInteger.valueOf(3)).signum() == 0, "Branch target is not an aligned executable label");
                require(delta.compareTo(ONE.shiftLeft(bits - 1).negate()) >= 0 && delta.compareTo(ONE.shiftLeft(bits - 1)) < 0, "Branch/JAL target is out of range");
                String synthetic = "__gnu_branch_" + fragment.address();
                program.getLocalSymbolTable().addSymbol(new Token(TokenTypes.IDENTIFIER, synthetic, program, fragment.line, 1), target.number.intValue(), false, errors);
                args.set(targetIndex, synthetic);
            }
            for (int i = 0; i < args.size() - (branch ? 1 : 0); i++) {
                String arg = args.get(i);
                if (RegisterFile.getRegister(arg) != null || FloatingPointRegisterFile.getRegister(arg) != null ||
                        ControlAndStatusRegisterFile.getRegister(arg) != null || matches(ROUNDING_MODE, arg)) continue;
                if (arg.endsWith(")")) {
                    int paren = arg.lastIndexOf('(');
                    require(paren >= 0 && RegisterFile.getRegister(arg.substring(paren + 1, arg.length() - 1)) != null, "Invalid base register");
                    String expr = arg.substring(0, paren);
                    BigInteger value = evaluate(expr.isEmpty() ? "0" : expr, fragment.line, fragment.section, fragment.offset, false).number;
                    args.set(i, value + arg.substring(paren));
                } else args.set(i, evaluate(arg, fragment.line, fragment.section, fragment.offset, false).number.toString());
            }
            return op + " " + String.join(",", args);
        }
        private void encode(String text, Fragment fragment, ArrayList<ProgramStatement> machine) {
            TokenList tokens = new Tokenizer(program).tokenizeLine(fragment.line, text, errors, false);
            require(!tokens.isEmpty(), "Empty instruction expansion");
            ArrayList<Instruction> matches = Globals.instructionSet.matchOperator(tokens.get(0).getValue());
            require(matches != null && !matches.isEmpty(), "Unsupported instruction: " + text);
            Instruction instruction = OperandFormat.bestOperandMatch(tokens, matches);
            require(instruction instanceof BasicInstruction && OperandFormat.tokenOperandMatch(tokens, instruction, errors), "Invalid basic instruction: " + text);
            SourceLine source = program.getSourceLineList().get(fragment.line - 1);
            ProgramStatement statement = new ProgramStatement(program, source.getSource(), program.getTokenList().get(fragment.line - 1), tokens,
                    instruction, (int)fragment.address(), source.getSourcePath(), source.getLineNumber(), List.of());
            statement.buildBasicStatementFromBasicInstruction(errors);
            statement.buildMachineStatementFromBasicStatement(errors);
            store(statement, machine);
        }
        private void store(ProgramStatement statement, ArrayList<ProgramStatement> machine) {
            try { Globals.memory.setStatement(statement.getAddress(), statement); }
            catch (AddressErrorException exception) { throw new Invalid(exception.getMessage()); }
            machine.add(statement);
        }

        private Value evaluate(String expression, int line, Section section, int offset, boolean absolute) {
            Value value = new Expression(expression, line, section, offset, absolute, new HashSet<>()).parse();
            if (value.section != null) {
                require(value.number.signum() >= 0 && value.number.bitLength() <= 32, "Symbol address exceeds the Core's mapped 32-bit domain");
                int address = value.number.intValue();
                require(value.section.executable() ? Memory.inTextSegment(address) : Memory.inDataSegment(address), "Symbol address is outside mapped memory");
            }
            return value;
        }
        /**
         * A local name resolves to this unit's definition. A name the unit declares global or
         * weak resolves to the link's winning definition, as an ld relocation would, so a weak
         * definition another unit overrides, or a COMDAT copy an earlier unit supplied, is not
         * used. A name defined nowhere falls back to an ld-provided array bound, and an
         * undefined weak reference is zero.
         */
        private Value resolve(String name, int line, Section section, int offset, boolean absolute, Set<String> visiting) {
            Symbol symbol;
            if (matches(NUMERIC_REFERENCE, name)) {
                List<Symbol> candidates = numeric.getOrDefault(name.substring(0, name.length() - 1), List.of());
                symbol = null;
                for (Symbol candidate : candidates) {
                    if (name.endsWith("b") && candidate.line <= line) symbol = candidate;
                    if (name.endsWith("f") && candidate.line > line) { symbol = candidate; break; }
                }
            } else symbol = symbols.get(name);
            if (!absolute && (symbol == null || declared.contains(name))) {
                Symbol global = globals.get(name);
                if (global != null && global != symbol) return global.unit.valueOf(global, visiting);
            }
            if (symbol == null && absolute) symbol = forwardAliases.get(name);
            if (symbol == null && !absolute) {
                int bounds = arrayBound(name);
                if (bounds >= 0) {
                    Section range = arrayRanges.get(bounds);
                    return new Value(BigInteger.valueOf(range.base + (name.endsWith("_end") ? range.size : 0)), range, true);
                }
                if (legacyGlobals) {
                    int address = Globals.symbolTable.getAddress(name);
                    if (address != SymbolTable.NOT_FOUND)
                        return new Value(BigInteger.valueOf(Integer.toUnsignedLong(address)), legacySection(address), true);
                }
                if (weak.contains(name)) return new Value(ZERO, null, true);
            }
            require(symbol != null, "Unresolved symbol: " + name);
            return valueOf(symbol, absolute, visiting);
        }
        Value valueOf(Symbol symbol, Set<String> visiting) { return valueOf(symbol, false, visiting); }
        private Value valueOf(Symbol symbol, boolean absolute, Set<String> visiting) {
            if (symbol.alias != null) {
                // Units can reuse local names, so the cycle check names the defining unit too.
                String key = index + ":" + symbol.name;
                require(visiting.size() < 64, "Alias nesting exceeds 64 levels");
                require(visiting.add(key), "Cyclic symbol alias: " + symbol.name);
                Value value = new Expression(symbol.alias, symbol.line, symbol.section, symbol.offset, absolute, visiting).parse();
                visiting.remove(key); return value;
            }
            require(!absolute, "Layout expression depends on a section/symbol: " + symbol.name);
            require(!symbol.section.discarded(), "Runtime reference to discarded section: " + symbol.name);
            return new Value(BigInteger.valueOf(symbol.section.base + symbol.offset), symbol.section, true);
        }
        private final class Expression {
            final String text;
            final int line, offset;
            final Section section;
            final boolean absolute;
            final Set<String> visiting;
            int position;
            int depth;
            Expression(String text, int line, Section section, int offset, boolean absolute, Set<String> visiting) {
                this.text = text; this.line = line; this.section = section; this.offset = offset; this.absolute = absolute; this.visiting = visiting;
                require(text.length() <= 4096, "Expression exceeds 4096 characters");
            }
            Value parse() { Value value = sum(); white(); require(position == text.length(), "Unsupported expression: " + text); return value; }
            void white() { while (position < text.length() && Character.isWhitespace(text.charAt(position))) position++; }
            Value sum() {
                Value left = unary(); white();
                while (position < text.length() && (text.charAt(position) == '+' || text.charAt(position) == '-')) {
                    boolean subtract = text.charAt(position++) == '-'; Value right = unary();
                    Section result = left.section;
                    if (subtract) {
                        if (right.section != null) { require(left.section == right.section, "Symbol difference requires the same section"); result = null; }
                    } else {
                        require(left.section == null || right.section == null, "Cannot add two section addresses");
                        if (right.section != null) result = right.section;
                    }
                    left = new Value(subtract ? left.number.subtract(right.number) : left.number.add(right.number), result, left.symbolic || right.symbolic); white();
                }
                return left;
            }
            Value unary() {
                require(++depth <= 64, "Expression nesting exceeds 64 levels");
                try { return atom(); } finally { depth--; }
            }
            Value atom() {
                white(); require(position < text.length(), "Missing expression operand");
                char c = text.charAt(position);
                if (c == '+' || c == '-') {
                    position++; Value value = unary(); require(c != '-' || value.section == null, "Cannot negate a section address");
                    return new Value(c == '-' ? value.number.negate() : value.number, value.section, value.symbolic);
                }
                if (c == '(') { position++; Value value = sum(); white(); require(position < text.length() && text.charAt(position++) == ')', "Unclosed expression"); return value; }
                int start = position;
                while (position < text.length() && (Character.isLetterOrDigit(text.charAt(position)) || ".$_".indexOf(text.charAt(position)) >= 0)) position++;
                require(position > start, "Unsupported expression: " + text);
                String atom = text.substring(start, position);
                if (atom.equals(".")) { require(!absolute, "Layout expression depends on current location"); return new Value(BigInteger.valueOf(section.base + offset), section, true); }
                if (!Character.isDigit(atom.charAt(0)) || matches(NUMERIC_REFERENCE, atom)) return resolve(atom, line, section, offset, absolute, visiting);
                try {
                    boolean hex = atom.startsWith("0x") || atom.startsWith("0X"), binary = atom.startsWith("0b") || atom.startsWith("0B");
                    BigInteger number = hex ? new BigInteger(atom.substring(2), 16) : binary ? new BigInteger(atom.substring(2), 2) : new BigInteger(atom, atom.startsWith("0") && atom.length() > 1 ? 8 : 10);
                    require(number.bitLength() <= 256, "Integer expression exceeds 256-bit limit"); return new Value(number, null, false);
                } catch (NumberFormatException exception) { throw new Invalid("Invalid integer: " + atom); }
            }
        }
    }

    private static BigInteger signedLow(BigInteger value) { return value.add(BigInteger.valueOf(0x800)).and(BigInteger.valueOf(0xfff)).subtract(BigInteger.valueOf(0x800)); }
    private static int integerWidth(String op) {
        switch (op) {
            case ".byte": return 1;
            case ".half": case ".short": case ".2byte": return 2;
            case ".word": case ".long": case ".4byte": return 4;
            case ".quad": case ".dword": case ".8byte": return 8;
            default: return 0;
        }
    }
    private static String modifierExpression(String text, int start) {
        int open = text.indexOf('(', start), level = 1, end = open + 1;
        while (end < text.length() && level > 0) { char c = text.charAt(end++); if (c == '(') level++; if (c == ')') level--; }
        require(level == 0, "Unclosed address modifier"); return text.substring(open + 1, end - 1);
    }
    private static List<String> arguments(String text) {
        List<String> result = new ArrayList<>();
        if (text.trim().isEmpty()) return result;
        boolean quote = false, escape = false;
        int depth = 0, start = 0;
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            if (escape) { escape = false; continue; }
            if (quote && c == '\\') { escape = true; continue; }
            if (c == '"') quote = !quote;
            if (!quote) {
                if (c == '(') depth++;
                if (c == ')') depth--;
                require(depth >= 0, "Unbalanced operands");
                if (c == ',' && depth == 0) { result.add(text.substring(start, i).trim()); start = i + 1; }
            }
        }
        require(!quote && depth == 0, "Unclosed string or operand");
        result.add(text.substring(start).trim()); return result;
    }
    private static String uncomment(String text) {
        boolean quote = false, escape = false;
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            if (escape) { escape = false; continue; }
            if (quote && c == '\\') { escape = true; continue; }
            if (c == '"') quote = !quote;
            if (!quote && c == '#') return text.substring(0, i);
        }
        return text;
    }
    private static boolean hasStatementSeparator(String text) {
        boolean quote = false, escape = false;
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            if (escape) { escape = false; continue; }
            if (quote && c == '\\') { escape = true; continue; }
            if (c == '"') quote = !quote;
            if (c == ';' && !quote) return true;
        }
        return false;
    }
    private static String unquote(String text) { require(text.length() >= 2 && text.startsWith("\"") && text.endsWith("\""), "Expected a quoted string"); return text.substring(1, text.length() - 1); }
    private static String unquoteOptional(String text) { return text.startsWith("\"") ? unquote(text) : text; }
    private static boolean allZero(byte[] bytes) { for (byte value : bytes) if (value != 0) return false; return true; }
    private static byte[] stringBytes(String text) {
        String source = unquote(text);
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        StringBuilder literal = new StringBuilder();
        for (int i = 0; i < source.length(); i++) {
            char c = source.charAt(i);
            if (c != '\\') { literal.append(c); continue; }
            byte[] utf = Utf8.encode(literal); output.write(utf, 0, utf.length); literal.setLength(0);
            require(++i < source.length(), "Incomplete string escape");
            c = source.charAt(i);
            int value;
            if (c >= '0' && c <= '7') {
                value = c - '0'; int digits = 1;
                while (digits < 3 && i + 1 < source.length() && source.charAt(i + 1) >= '0' && source.charAt(i + 1) <= '7') { value = value * 8 + source.charAt(++i) - '0'; digits++; }
                require(value <= 255, "Octal string escape exceeds one byte");
            } else if (c == 'x') {
                value = 0; int digits = 0;
                while (i + 1 < source.length() && Character.digit(source.charAt(i + 1), 16) >= 0) { value = value * 16 + Character.digit(source.charAt(++i), 16); digits++; require(value <= 255, "Hex string escape exceeds one byte"); }
                require(digits > 0, "Empty hex string escape");
            } else {
                switch (c) {
                    case 'n': value = 10; break; case 'r': value = 13; break; case 't': value = 9; break;
                    case 'b': value = 8; break; case 'f': value = 12; break; case 'v': value = 11; break;
                    case 'a': value = 7; break; case '"': value = 34; break; case '\\': value = 92; break;
                    default: throw new Invalid("Unsupported string escape: \\" + c);
                }
            }
            output.write(value);
        }
        byte[] utf = Utf8.encode(literal); output.write(utf, 0, utf.length);
        return output.toByteArray();
    }
}
