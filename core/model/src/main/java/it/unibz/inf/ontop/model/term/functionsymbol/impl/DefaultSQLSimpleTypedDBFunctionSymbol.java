package it.unibz.inf.ontop.model.term.functionsymbol.impl;

import it.unibz.inf.ontop.model.type.DBTermType;

public class DefaultSQLSimpleTypedDBFunctionSymbol extends AbstractSimpleTypedDBFunctionSymbol {


    protected DefaultSQLSimpleTypedDBFunctionSymbol(String nameInDialect, int arity, DBTermType targetType,
                                                    boolean isInjective, DBTermType rootDBTermType) {
        super(nameInDialect, arity, targetType, isInjective, rootDBTermType);
    }
}
