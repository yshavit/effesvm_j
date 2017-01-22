package com.yuvalshavit.effesvm.load;

import com.yuvalshavit.effesvm.runtime.EffesType;
import com.yuvalshavit.effesvm.runtime.PcMove;

public interface LinkContext {
  EffesType type(String typeName);
  EffesFunction<?> getFunctionInfo(EffesFunction.Id function);
  PcMove firstOpOf(EffesFunction.Id id);
  EffesFunction<?> getCurrentLinkingFunctionInfo();
}
