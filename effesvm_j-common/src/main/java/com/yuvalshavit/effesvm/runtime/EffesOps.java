package com.yuvalshavit.effesvm.runtime;

import com.yuvalshavit.effesvm.ops.OperationFactory;

public interface EffesOps<T> {
  
  @OperationFactory("arry")
  void createArray(T context);

  @OperationFactory("int")
  void pushInt(T context, String value);

  @OperationFactory("pop")
  void pop(T context);

  @OperationFactory("copy")
  void copy(T context);
  
  @OperationFactory("pvar")
  void pvar(T context, String n);

  @OperationFactory("svar")
  void svar(T context, String n);
  
  @OperationFactory("Svar")
  void Svar(T context, String n);

  @OperationFactory("goto")
  void gotoAbs(T context, String n);

  @OperationFactory("goif")
  void gotoIf(T context, String n);

  @OperationFactory("gofi")
  void gotoIfNot(T context, String n);

  @OperationFactory("rtrn")
  void rtrn(T context);

  @OperationFactory("type")
  void type(T context, String typeName);

  @OperationFactory("typp")
  void typp(T context, String typeName);

  @OperationFactory("typf")
  void typf(T context, String typeName);

  @OperationFactory("pfld")
  void pushField(T context, String typeName, String fieldName);

  @OperationFactory("Pfld")
  void PushField(T context, String typeName, String fieldName);
  
  @OperationFactory("sfld")
  void storeField(T context, String typeName, String fieldName);

  @OperationFactory("call_Array:store")
  void arrayStore(T context);

  @OperationFactory("call_Array:get")
  void arrayGet(T context);

  @OperationFactory("call_Array:len")
  void arrayLen(T context);

  @OperationFactory("call_native:toString")
  void nativeToString(T context);

  @OperationFactory("call_native:toStringPretty")
  void nativeToStringPretty(T context);

  @OperationFactory("call_Integer:parse")
  void parseInt(T context);

  @OperationFactory("call_Integer:lt")
  void lt(T context);

  @OperationFactory("call_Integer:le")
  void le(T context);

  @OperationFactory("call_Integer:eq")
  void eq(T context);

  @OperationFactory("call_Integer:ne")
  void ne(T context);

  @OperationFactory("call_Integer:ge")
  void ge(T context);

  @OperationFactory("call_Integer:gt")
  void gt(T context);

  @OperationFactory("call")
  void call(T context, String scopeSpecifier, String functionName);

  @OperationFactory("call_Integer:add")
  void iAdd(T context);

  @OperationFactory("call_Integer:sub")
  void iSub(T context);

  @OperationFactory("call_Integer:mult")
  void iMul(T context);

  @OperationFactory("call_Integer:div")
  void iDiv(T context);

  @OperationFactory("debug-print")
  void debugPrint(T context);

  @OperationFactory("debug-print-pretty")
  void debugPrintPretty(T context);

  @OperationFactory("bool")
  void bool(T context, String sValue);

  @OperationFactory("call_Boolean:negate")
  void negate(T context);

  @OperationFactory("call_Boolean:and")
  void and(T context);

  @OperationFactory("call_Boolean:or")
  void or(T context);
  
  @OperationFactory("call_Boolean:xor")
  void xor(T context);

  @OperationFactory("call_Match:igroup")
  void matchIndexedGroup(T context);

  @OperationFactory("call_Match:ngroup")
  void matchNamedGroup(T context);

  @OperationFactory("call_Match:groupCount")
  void matchGroupCount(T context);

  @OperationFactory("call_Match:tail")
  void matchTail(T context);

  @OperationFactory("str")
  void strPush(T context, String value);

  @OperationFactory("call_String:len")
  void stringLen(T context);

  @OperationFactory("call_String:regex")
  void stringRegex(T context);

  @OperationFactory("call_String:sout")
  void sout(T context);

  @OperationFactory("call_String:concat")
  void concat(T context);

  @OperationFactory("call_Stream:stdout")
  void stdout(T context);

  @OperationFactory("call_Stream:stdin")
  void stdin(T context);

  @OperationFactory("call_Stream:writeFile")
  void writeFile(T context);

  @OperationFactory(("call_Stream:writeText"))
  void writeText(T context);

  @OperationFactory("call_Stream:readFile")
  void readFile(T context);

  @OperationFactory("call_Stream:readLine")
  void readLine(T context);

  @OperationFactory("call_Stream:stdinLine")
  void stdinLine(T context);

  @OperationFactory("call_StringBuilder:add")
  void stringBuilderAdd(T context);

  @OperationFactory("call_StringBuilder:get")
  void stringBuilderGet(T context);

  @OperationFactory("labl")
  void label(T context, String name);

  @OperationFactory("sbld")
  void sbld(T context);

  @OperationFactory("fail")
  void fail(T context, String message);
}
