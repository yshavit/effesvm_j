The VM runtime
========================================================================================

This document outlines the internals of effesvm-j. Much of the functionality is described in [bytecode.md](bytcode.md) and [vm-stack.md](vm-stack.md), and those parts will not be repeated here.

Opcode internals
========================================================================================

Each `.efct` file will compile down to an array of Operation objects:

    public interface Operation {
      PcMove run(EffesState state);
    }

`EffesState` is an abstraction over the stack's raw `List<Object>` representation. It encapsulates operations like "pop a value," including whatever range checks are needed for those operations. It also includes all registers.

Each `Operation` instance represents an opcode plus its arguments. In other words, it's all of the data in one line. Each opcode is a different subclass of `Operation`, and its `run` implementation encodes the opcode's functionality.

The VM's program counter is represented by the _$pc_ register. It is an `int` representing an index into the compiled `Operation[]`.(When I introduce multi-module compilation, the PC will include the module as well as offset.)

`PcMove` is class with a private constructor, and tells the Effes VM how to adjust the _$pc_ register after the `Operation` complete. There are two factory methods:

- `PcMove.next()` increments _$pc_ by one
- `PcMove.absolute(int)` sets _$pc_ to the provided number, as an absolute offset into compiled `Operation[]`s.
