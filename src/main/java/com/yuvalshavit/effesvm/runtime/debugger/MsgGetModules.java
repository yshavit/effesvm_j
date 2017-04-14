package com.yuvalshavit.effesvm.runtime.debugger;

import java.io.Serializable;
import java.util.ArrayList;
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

    Map<String,Map<String,List<String>>> modulesPlaintext = new HashMap<>(modules.size());
    modules.forEach( (moduleId, module) -> {
      Collection<EffesFunction<Operation>> functions = module.functions();
      Map<String,List<String>> functionsPlaintext = new HashMap<>(functions.size());
      for (EffesFunction<Operation> function : functions) {
        List<String> ops = new ArrayList<>(function.nOps());
        for (int i = 0; i < function.nOps(); ++i) {
          ops.add(function.opAt(i).toString());
        }
        functionsPlaintext.put(function.id().toString(), ops);
      }
      modulesPlaintext.put(moduleId.toString(), functionsPlaintext);
    });
    return new Response(modulesPlaintext);
  }

  public static class Response implements Serializable {

    private final Map<String,Map<String,List<String>>> functions;

    public Response(Map<String, Map<String, List<String>>> functions) {
      this.functions = functions;
    }

    public Map<String,Map<String,List<String>>> functionsByModule() {
      return functions;
    }
  }
}
