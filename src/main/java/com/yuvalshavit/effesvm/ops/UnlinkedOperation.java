package com.yuvalshavit.effesvm.ops;

import java.util.function.Function;

import com.yuvalshavit.effesvm.load.LinkContext;

public interface UnlinkedOperation extends Function<LinkContext,Operation> {
  interface Body extends Function<LinkContext,Operation.Body> {}
}
