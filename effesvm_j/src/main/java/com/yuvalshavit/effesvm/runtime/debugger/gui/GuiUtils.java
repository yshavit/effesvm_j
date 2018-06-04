package com.yuvalshavit.effesvm.runtime.debugger.gui;

import java.util.Comparator;

import com.yuvalshavit.effesvm.load.EfctScope;
import com.yuvalshavit.effesvm.load.EffesFunctionId;

class GuiUtils {
  private GuiUtils() {}

  static final Comparator<EffesFunctionId> sortByModuleThenFunction = (a, b) -> {
      EfctScope aScope = a.getScope();
      EfctScope bScope = b.getScope();
      int cmp = aScope.getModuleId().toString().compareTo(bScope.getModuleId().toString());
      if (cmp == 0) {
        String aType = aScope.map(m -> "", (m, t) -> t);
        String bType = bScope.map(m -> "", (m, t) -> t);
        cmp = aType.compareTo(bType);
      }
      if (cmp == 0) {
        cmp = a.getFunctionName().compareTo(b.getFunctionName());
      }
      return cmp;
    };
}
