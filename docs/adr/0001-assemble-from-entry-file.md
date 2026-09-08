# Assemble from one entry file

An assembly request supplies a Source Set and one Entry File. Assembly follows `.include` directives transitively from that file and ignores unreferenced files, because treating every supplied file as a compilation unit would both weaken the meaning of `.include` and assemble included source more than once.
