# Require a source set and entry file

Program construction always requires a Source Set and an Entry File, and the single-source factory is removed from both the Java core and JavaScript package. A uniform construction model gives every source a meaningful path and avoids maintaining separate compilation paths; a one-file caller supplies a one-entry Source Set.
