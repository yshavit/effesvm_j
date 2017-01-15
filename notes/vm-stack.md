Basic structure, types and considerations
========================================================================================

The stack frame is maintained on a LIFO `List<Object>` stack within the JVM.

Optimization is not a main concern, for several reasons:

- First and foremost, the very nature of creating a VM within a VM is inefficient, and the only reason to do it is simplicity. When optimization becomes a serious concern, the first task will be to compile Effes classes into Java bytecode directly rather than to Effes bytecode; this should yield immense gains on its own.
- The JVM's GC precludes us from popping whole frames at once just by changing an _$sp_ register, as a real stack would do. If we did that, the leftover values would keep strong references to their respective objects, which would be a memory leak. Since we have to null those values one at a time anyway, various optimizations are instantly lost to us.
- Since we need to work with object references (for Effes objects), we can't easily work directly with primitives: we have to use boxed types. We could get around that problem (e.g. with multiple stacks per Java type, or by using `java.sun.Unsafe`), but as mentioned above, the biggest win would just be to compile to `.class` files.

The Effes stack's elements are references to the following classes:

- FrameInfo
- EffesObj
- other Java classes used to represent Effes native types (ints, etc)

An EffesObj contains:

- an EffesType reference
- 0 or more constructor arguments, which can be accessed via the _pvar_ opcode (described in the [bytecode spec](bytecode.md))

The FrameInfo object is described below.

Stack frames
========================================================================================

Stack frames look like:

    [ ...                ] <-- $sp ╮
    [ ...                ]         ├ local stack
    [ localStack0        ] ────────╯
    [ localVarN          ] ────────╮
    [ ...                ]         ├ local variables
    [ localVar0          ] ────────╯
    [ FrameInfo          ] <-- $fp
    [ argN               ] ────────╮
    [ ...                ]         ├ method arguments
    [ arg0               ] ────────╯
    <---previous frame--->

The stack's registers are:

- _$rv_: the return value for the current stack frame
- _$fp_: the current stack frame's FrameInfo reference
- _$sp_: the top of the stack, inclusive
- _$pc_: the program counter (format not specified in this document)

These registers cannot be manipulated directly by opcodes.

The stack frame is divided into three sections:

- the method arguments and local variables, which are fixed for the existence of the frame (but may be different from frame to frame)
- the local stack, which can grow as needed (subject to overall stack size constraints, of course)

The FrameInfo contains:

- the number of arguments in this this frame
- the number of local variables in this frame
- the previous frame's _$fp_
- the invoking frame's returning _$pc_

Stack operations
========================================================================================

Pushing a value
----------------------------------------------------------------------------------------

Several opcodes push a value to the stack. These operations succeed as long as there is room on the stack. They increment the _$sp_ register.

Popping a value
----------------------------------------------------------------------------------------

Several opcodes pop values from the stack. These operations succeeds as long as there are elements on the local stack; that is, that _$sp > $fp + nLocal_, where _nLocal_ is the number of local variables. Popping a value not only decrements _$sp_, but also nulls out the value being popped.

Opening a frame
----------------------------------------------------------------------------------------

To invoke a method with N arguments:

- Push the arguments, in order. The first argument pushed will be the bottom-most value in the stack
- Invoke the `call` opcode. This will
  - look up the number of arguments and local variables for the method being called
  - confirm that the current frame's local stack contains at least that many elements
  - construct a new FrameInfo object with:
    - the current _$fp_
    - and the invoked method's number of method arguments and local variables
    - a pc corresponding to the opcode that you wish to return to (typically the one after this method call)
  - push the new FrameInfo object to the stack, and set _$fp_ to point to it
  - set _$sp_ to _$fp + nLocal_, where _nLocal_ is the number of local variable for the method being called
  - update _$pc_ to point to the new method's first opcode

Copying an argument or local variable to the stack
----------------------------------------------------------------------------------------

The `parg` opcode copies argument methods to the top of the stack. It first checks that the requested argument is witing range (that is, that _0 ≤ N < nArgs_, where N is the argument being requested and nArgs is the number of arguments in the current stack frame). It then pushes this argument to the top of the local stack.

The `gvar` opcode copies a variable to the top of the stack. It works similarly, except that there is also a check that the variable has been set.

Popping a local stack value to a local variable
----------------------------------------------------------------------------------------

This pops a value from the local stack to a local variable. No type checking is done. The variable must be in range for the current FrameInfo's local variables cont.
    
Closing a frame
----------------------------------------------------------------------------------------

As mentioned above, can't just pop by setting registers, as a real stack would, because we need to null out elements so that the JVM's GC can collect those objects.

- Check that _$rv_ has been set
- Let _fp_ be current FramePointer info
- pop elements from the stack while _$sp > $fp - fp.nArgs_
- set _$fp = fp.previousFp_
- set _$pc = fp.returningPc_
- push _$rv_, and null it
