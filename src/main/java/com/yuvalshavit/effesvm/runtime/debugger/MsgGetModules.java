package com.yuvalshavit.effesvm.runtime.debugger;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.BitSet;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.yuvalshavit.effesvm.load.EffesFunction;
import com.yuvalshavit.effesvm.load.EffesModule;
import com.yuvalshavit.effesvm.ops.Operation;
import com.yuvalshavit.effesvm.runtime.DebugServerContext;

public class MsgGetModules extends Msg<MsgGetModules.Response> {

  public MsgGetModules() {
    super(MsgGetModules.Response.class);
  }

  @Override
  Response process(DebugServerContext context, DebuggerState state) throws InterruptedException {
    Map<EffesModule.Id,EffesModule<Operation>> modules = context.modules();

    Map<String,Map<String,FunctionInfo>> modulesPlaintext = new HashMap<>(modules.size());
    modules.forEach( (moduleId, module) -> {
      Collection<EffesFunction<Operation>> functions = module.functions();
      Map<String,FunctionInfo> functionsPlaintext = new HashMap<>(functions.size());
      String moduleIdStr = moduleId.toString();
      for (EffesFunction<Operation> function : functions) {
        List<String> ops = new ArrayList<>(function.nOps());
        for (int i = 0; i < function.nOps(); ++i) {
          ops.add(function.opAt(i).toString());
        }
        String functionId = function.id().toString();
        BitSet breakpoints = state.getDebugPoints(moduleIdStr, functionId);
        functionsPlaintext.put(functionId, new FunctionInfo(ops, breakpoints));
      }
      modulesPlaintext.put(moduleIdStr, functionsPlaintext);
    });
    return new Response(modulesPlaintext);
  }

  public static class Response implements Serializable {

    private final Map<String,Map<String,FunctionInfo>> functions;

    public Response(Map<String, Map<String,FunctionInfo>> functions) {
      this.functions = functions;
    }

    public Map<String,Map<String,FunctionInfo>> functionsByModule() {
      return functions;
    }
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
