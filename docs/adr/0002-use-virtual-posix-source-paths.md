# Use virtual POSIX source paths

Source Sets use canonical, root-relative, case-sensitive POSIX paths regardless of the JavaScript host. Relative include paths resolve from the including file and normalize `.` and `..`; a leading `/` resolves from the virtual root, while traversal above that root is an error. This gives browsers, Node, Windows, and Unix hosts identical include behavior without exposing the host filesystem.
