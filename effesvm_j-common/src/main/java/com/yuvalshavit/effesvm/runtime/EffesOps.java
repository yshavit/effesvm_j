package com.yuvalshavit.effesvm.runtime;

import com.yuvalshavit.effesvm.ops.OperationFactory;

public interface EffesOps<T> {
  
  @OperationFactory("arry")
  T createArray();

  @OperationFactory("int")
  T pushInt(String value);

  @OperationFactory("pop")
  T pop();
  
  @OperationFactory("pvar")
  T pvar(String n);

  @OperationFactory("svar")
  T svar(String n);
  
  @OperationFactory("Svar")
  T Svar(String n);

  @OperationFactory("goto")
  T gotoAbs(String n);

  @OperationFactory("goif")
  T gotoIf(String n);

  @OperationFactory("gofi")
  T gotoIfNot(String n);

  @OperationFactory("rtrn")
  T rtrn();

  @OperationFactory("type")
  T type(String typeName);

  @OperationFactory("typp")
  T typp(String typeName);

  @OperationFactory("typf")
  T typf(String typeName);

  @OperationFactory("pfld")
  T pushField(String typeName, String fieldName);

  @OperationFactory("Pfld")
  T PushField(String typeName, String fieldName);
  
  @OperationFactory("sfld")
  T storeField(String typeName, String fieldName);

  @OperationFactory("call_Array:store")
  T arrayStore();

  @OperationFactory("call_Array:get")
  T arrayGet();

  @OperationFactory("call_Array:len")
  T arrayLen();

  @OperationFactory("call_native:toString")
  T nativeToString();

  @OperationFactory("call_native:toStringPretty")
  T nativeToStringPretty();

  @OperationFactory("call_Integer:parse")
  T parseInt();

  @OperationFactory("call_Integer:lt")
  T lt();

  @OperationFactory("call_Integer:le")
  T le();

  @OperationFactory("call_Integer:eq")
  T eq();

  @OperationFactory("call_Integer:ne")
  T ne();

  @OperationFactory("call_Integer:ge")
  T ge();

  @OperationFactory("call_Integer:gt")
  T gt();

  @OperationFactory("call")
  T call(String scopeSpecifier, String functionName);

  @OperationFactory("call_Integer:add")
  T iAdd();

  @OperationFactory("call_Integer:sub")
  T iSub();

  @OperationFactory("call_Integer:mult")
  T iMul();

  @OperationFactory("call_Integer:div")
  T iDiv();

  @OperationFactory("debug-print")
  T debugPrint();

  @OperationFactory("debug-print-pretty")
  T debugPrintPretty();

  @OperationFactory("bool")
  T bool(String sValue);

  @OperationFactory("call_Boolean:negate")
  T negate();

  @OperationFactory("call_Boolean:and")
  T and();

  @OperationFactory("call_Boolean:or")
  T or();
  
  @OperationFactory("call_Boolean:xor")
  T xor();

  @OperationFactory("call_Match:igroup")
  T matchIndexedGroup();

  @OperationFactory("call_Match:ngroup")
  T matchNamedGroup();

  @OperationFactory("call_Match:groupCount")
  T matchGroupCount();

  @OperationFactory("str")
  T strPush(String value);

  @OperationFactory("call_String:len")
  T stringLen();

  @OperationFactory("call_String:regex")
  T stringRegex();

  @OperationFactory("call_String:sout")
  T sout();

  @OperationFactory("call_String:concat")
  T concat();

  @OperationFactory("call_Stream:stdin")
  T stdin();

  @OperationFactory("call_Stream:writeFile")
  T writeFile();

  @OperationFactory(("call_Stream:writeText"))
  T writeText();

  @OperationFactory("call_Stream:readFile")
  T readFile();

  @OperationFactory("call_Stream:readLine")
  T readLine();

  @OperationFactory("call_Stream:stdinLine")
  T stdinLine();

  @OperationFactory("call_StringBuilder:add")
  T stringBuilderAdd();

  @OperationFactory("labl")
  T label(String name);

  @OperationFactory("sbld")
  T sbld();

  @OperationFactory("fail")
  T fail(String message);
}
