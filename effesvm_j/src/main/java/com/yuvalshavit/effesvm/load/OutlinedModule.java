package com.yuvalshavit.effesvm.load;

import java.util.List;
import java.util.Map;

import com.yuvalshavit.effesvm.runtime.EffesType;

import lombok.Data;

@Data
public class OutlinedModule {
  private final Map<String,EffesType> types;
  private final Map<EffesFunctionId,FunctionParse> functions;

  @Override
  public String toString() {
    return super.toString();
  }

  @Data
  public static class FunctionParse {
    private final int nArgs;
    private final boolean hasReturnValue;
    private final List<EfctLine> lines;
  }

}
