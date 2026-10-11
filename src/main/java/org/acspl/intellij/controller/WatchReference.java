package org.acspl.intellij.controller;

import org.acspl.intellij.catalog.BuiltinCatalog;
import org.acspl.intellij.symbols.DeclarationModel;
import java.util.regex.Pattern;

/** Name resolution is necessary: foo(1) can be a function as well as an array in ACSPL+. */
public final class WatchReference {
    private static final Pattern BASE = Pattern.compile("^([A-Za-z_][A-Za-z_0-9]*)");
    private WatchReference() {}
    public static boolean local(String source, String expression) {
        ControllerProtocol.read(0, expression, false); // Validate syntax before scanning or querying.
        var match = BASE.matcher(expression); match.find();
        String name = match.group(1);
        boolean indexed = expression.substring(name.length()).startsWith("(");
        var declarations = DeclarationModel.scan(source).declarations();
        var declaration = declarations.stream()
            .filter(d -> d.name().equals(name) && d.kind().equals("variable") && d.visibleStart() == 0).findFirst().orElse(null);
        if (declaration != null) {
            if (indexed && !declaration.array()) throw new IllegalArgumentException("该声明不是数组：" + name);
            return declaration.scope() == DeclarationModel.Scope.LOCAL;
        }
        // An explicit function/parameter declaration must not fall back to a system name.
        if (declarations.stream().anyMatch(d -> d.name().equals(name)))
            throw new IllegalArgumentException("不支持读取函数、函数参数或函数内部局部变量：" + name);
        var builtin = BuiltinCatalog.getInstance().find(name);
        if (builtin == null || !builtin.kind().equals("variable") || indexed && !builtin.array())
            throw new IllegalArgumentException("只读取当前文件的缓冲区变量和已知系统变量；无法识别：" + name);
        return false;
    }
}
