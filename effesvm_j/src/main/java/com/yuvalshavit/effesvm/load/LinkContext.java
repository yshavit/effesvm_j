package com.yuvalshavit.effesvm.load;

import com.yuvalshavit.effesvm.runtime.EffesType;
import com.yuvalshavit.effesvm.runtime.PcMove;

public interface LinkContext {
  EffesModule.Id currentModule();
  EffesType type(EffesModule.Id id, String typeName);
  EffesFunction getFunctionInfo(EffesFunctionId id);
  int nOpsInCurrentFunction();
  int findLabelOpIndex(String label);
}
