package org.jruby.util;

import org.jruby.util.OpenvmsFilespecTranslate;

public class GetUnixPath {
    //
    // getUnixPath is a complete NO-OP opeation unless filename
    // is given a pure VMS file syntax containing the '[' and ']'
    // or '<' and '>' characters acting as directory delimiters.
    //
    public static String getUnixPath (String filename) {
        String path;

        path = OpenvmsFilespecTranslate.vmsFilespecToUnix(filename);
        if (path != null) {
            return path;
        }
        else
            return filename;
    }
}
