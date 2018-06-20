package com.yuvalshavit.effesvm.runtime.coverage;

import java.util.NavigableMap;

import com.yuvalshavit.effesvm.load.EffesFunctionId;
import com.yuvalshavit.effesvm.load.EffesModule;
import com.yuvalshavit.effesvm.util.Average;

import lombok.AllArgsConstructor;

@AllArgsConstructor
class Report {
  final Average overall;
  final NavigableMap<EffesFunctionId,Average> functionsAverages;
  final NavigableMap<EffesModule.Id,Average> modulesAverages;
}
