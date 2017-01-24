package com.yuvalshavit.effesvm.load;

import com.yuvalshavit.effesvm.runtime.EffesType;
import com.yuvalshavit.effesvm.runtime.PcMove;

public interface LinkContext {
  EffesType type(ScopeId id);
  EffesFunction<?> getFunctionInfo(ScopeId scope, String function);
  EffesFunction<?> getCurrentLinkingFunctionInfo();
  PcMove firstOpOf(ScopeId scopeId, String function);
}
