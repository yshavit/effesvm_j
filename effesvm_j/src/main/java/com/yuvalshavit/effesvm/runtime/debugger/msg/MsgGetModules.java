package com.yuvalshavit.effesvm.runtime.debugger.msg;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.BitSet;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.yuvalshavit.effesvm.load.EffesFunction;
import com.yuvalshavit.effesvm.load.EffesFunctionId;
import com.yuvalshavit.effesvm.load.EffesModule;
import com.yuvalshavit.effesvm.runtime.DebugServerContext;
import com.yuvalshavit.effesvm.runtime.debugger.DebuggerState;

import lombok.Data;

public class MsgGetModules extends Msg<MsgGetModules.Response> {

  public MsgGetModules() {
    super(MsgGetModules.Response.class);
  }

  @Override
  public Response process(DebugServerContext context, DebuggerState state) {
    Map<EffesModule.Id,EffesModule> modules = context.modules();

    Map<EffesModule.Id,Map<EffesFunctionId,FunctionInfo>> modulesPlaintext = new HashMap<>(modules.size());
    modules.forEach((moduleId, module) -> {
      Collection<EffesFunction> functions = module.functions();
      Map<EffesFunctionId,FunctionInfo> functionsPlaintext = new HashMap<>(functions.size());
      for (EffesFunction function : functions) {
        List<String> ops = new ArrayList<>(function.nOps());
        for (int i = 0; i < function.nOps(); ++i) {
          ops.add(function.opAt(i).toString());
        }
        BitSet breakpoints = state.getDebugPoints(function.id());
        functionsPlaintext.put(function.id(), new FunctionInfo(ops, breakpoints));
      }
      modulesPlaintext.put(moduleId, functionsPlaintext);
    });
    return new Response(modulesPlaintext);
  }

  @Data
  public static class Response implements Serializable {
    private final Map<EffesModule.Id,Map<EffesFunctionId,FunctionInfo>> functions;
  }

  public static class FunctionInfo implements Serializable {
    private final List<String> opDescriptions;
    private final BitSet breakpoints;

    public FunctionInfo(List<String> opDescriptions, BitSet breakpoints) {
      this.opDescriptions = opDescriptions;
      this.breakpoints = breakpoints;
    }

    public List<String> opDescriptions() {
      return opDescriptions;
    }

    public BitSet breakpoints() {
      return breakpoints;
    }
  }
}
