package org.jruby.compiler.ir;

import org.jruby.parser.StaticScope;

public class IRMetaClassBody extends IRClassBody {
    public IRMetaClassBody(IRManager manager, IRScope lexicalParent, String name,
            int lineNumber, StaticScope scope) {
        super(manager, lexicalParent, name, lineNumber, scope);
    }

    public IRMetaClassBody(IRManager manager, IRScope lexicalParent, String name,
            String fileName, int lineNumber, StaticScope scope) {
        super(manager, lexicalParent, name, fileName, lineNumber, scope);
    }

    @Override
    public String getScopeName() {
        return "MetaClassBody";
    }
}
