# Require a source location for statement lookup

The JavaScript API exposes `getStatementsAtSourceLocation(sourcePath, line)` and removes `getStatementAtSourceLine(line)`. A line number alone becomes ambiguous as soon as included files are supported, while returning only one statement would hide the multiple machine instructions produced by pseudo-instructions and macro expansions. The path-aware method therefore returns every corresponding machine statement, or an empty list when the location produces none.
