# RISC-V Assembly

This context defines the source-level concepts used to construct one assemblable RISC-V program.

## Language

**Source Set**:
A collection of named RISC-V source files available to one assembly. Merely belonging to the source set does not cause a file to be assembled.
_Avoid_: File list, compilation units

**Source Path**:
A canonical, root-relative, case-sensitive POSIX path that identifies a source file within a Source Set.
_Avoid_: Filename, host filesystem path

**Entry File**:
The source file where assembly begins. The assembled program consists of this file and the files it includes transitively.
_Avoid_: Entry point, main file

**Execution Entry Label**:
The label at which simulation begins after assembly, commonly `main`. It is independent of which source file is the Entry File.
_Avoid_: Entry file

**Include Directive**:
A source directive that textually inserts another source file at its location. Every occurrence is expanded independently; an inclusion cycle is invalid.
_Avoid_: Import, module import
