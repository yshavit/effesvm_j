package com.yuvalshavit.effesvm.ops;

import java.util.function.Function;

import com.yuvalshavit.effesvm.load.StaticContext;

public interface UnlinkedOperation extends Function<StaticContext,Operation> {
}
