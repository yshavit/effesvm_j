Basic structure, types and considerations
========================================================================================

The stack frame is maintained on a LIFO `Object[]` stack within the JVM.

Optimization is not a main concern, for several reasons:

- First and foremost, the very nature of creating a VM within a VM is inefficient, and the only reason to do it is simplicity of the `.efct` file format. When optimization becomes a serious concern, the first task will be to compile Effes classes into Java bytecode directly rather than to Effes bytecode; this should yield immense gains on its own.
- The JVM's GC precludes us from popping whole frames at once just by changing an _$sp_ register, as a real stack would do. If we did that, the leftover values would keep strong references to their respective objects, which would be a memory leak. Since we have to null those values one at a time anyway, various optimizations are instantly lost to us.
- Since we need to work with object references (for Effes objects), we can't easily work directly with primitives: we have to use boxed types. We could get around that problem (e.g. with multiple stacks per Java type, or by using `java.sun.Unsafe`), but as mentioned above, the biggest win would just be to compile to `.class` files.

The Effes stack's elements are references to the following classes:

- FrameInfo
- EffesRef&lt;?&gt;, which can be:
  - EffesObjects, representing user-defined types; or
  - EffesNativeObject, representing built-in types

An EffesObj contains:

- an EffesType reference
- 0 or more constructor arguments, which can be accessed via the _pvar_ opcode (described in the [bytecode spec](bytecode.md))

Note that "native," in EffesNativeObject and all other contexts, means "native to the EVM." It does _not_ refer to Java's `native` keyword or functionality.

The FrameInfo object is described below.

Stack frames
========================================================================================

Stack frames look like:

    [ ...                ] <-- $sp ╮
    [ ...                ]         ├ local stack
    [ localStack0        ] ────────╯
    [ FrameInfo          ] <-- $fp
    [ argN               ] ────────╮
    [ ...                ]         ├ local variables (including method arguments)
    [ arg0               ] ────────╯
    <---previous frame--->

The stack's registers are:

- _$fp_: the current stack frame's FrameInfo reference
- _$sp_: the top of the stack, inclusive
- _$pc_: the program counter (format not specified in this document)

These registers cannot be manipulated directly by opcodes.

The stack frame is divided into three sections:

- the method arguments and local variables, which are fixed for the existence of the frame (but may be different from frame to frame)
- the FrameInfo object
- the local stack, which can grow as needed (subject to overall stack size constraints, of course)

The FrameInfo contains:

- the number of arguments in this this frame
- the number of local variables in this frame
- the previous frame's _$fp_
- the invoking frame's returning _$pc_

The local variables consists of both the method arguments and any other local variable slots the method has requested. These are accessed identically; the only difference is that the arguments come first in order, and are "stolen" from the previous stack frame.

For instance, let's say a stack frame has three elements in its local stack: `a`, `b` and `c` (with `c` being the topmost element, the one that _$sp_ points to). The EVM invokes a method that takes two arguments and two local variables. The new frame's local variables section will consist of `b`, `c`, `d'` and `e'`, where `d'` and `e'` are empty slots. When the stack frame closes, the previous stack frame will now consist of `a`, `r` where `r` is the method's return value.

Stack operations
========================================================================================

Pushing and popping
----------------------------------------------------------------------------------------

Several opcodes push a value to the stack. These operations succeed as long as there is room on the stack. They increment the _$sp_ register.

Several opcodes pop values from the stack. These operations succeeds as long as there are elements on the local stack; that is, that _$sp > $fp + nLocal_, where _nLocal_ is the number of local variables. Popping a value not only decrements _$sp_, but also nulls out the value being popped.

When multiple items need to be pushed or popped as part of a single semantic action (for instance, invoking a function or performing arithmetic), the convention is for them to be pushed in caller-order. That is, the first element pushed is the first element as seen from the caller's perspective. This means that the receiver will see them in the _opposite_ order. For instance, to perform `5 - 3`, you would do:

    int  5
    int  3
    isub

From the perspective of `isub`, these seem to come in reverse order. The first item it sees is 3, and the second is 5. This is a convention, not a rule. If a specific opcode finds it much more convenient to work in a different order, it may do so.

Pushing items in caller order has the advantage of having bytecode execution more closely mirror code. For instance, if a program executes `foo() + bar()`, pushing `foo()`'s result first is more intuitive. If `foo()` and `bar()` have side effects, this is visible to end users.
  

Opening a frame
----------------------------------------------------------------------------------------

To invoke a method with N arguments:

- Push the arguments, in order. The first argument pushed will be the bottom-most value in the stack
- Invoke the `call` opcode. This will
  - look up the number of arguments and local variables for the method being called
  - confirm that the current frame's local stack contains at least as many arguments as are required
  - allocate space for the local variables
  - construct a new FrameInfo object with:
    - the current _$fp_
    - and the invoked method's number of method arguments and local variables
    - a pc corresponding to the opcode that you wish to return to (typically the one after this method call)
  - push the new FrameInfo object to the stack, and set _$fp_ to point to it
  - set _$sp_ to _$fp + nLocal_, where _nLocal_ is the number of local variable for the method being called
  - update _$pc_ to point to the new method's first opcode
    
Closing a frame
----------------------------------------------------------------------------------------

As mentioned above, can't just pop by setting registers, as a real stack would, because we need to null out elements so that the JVM's GC can collect those objects.

The steps to closing a frame are:

- require exactly one element on the local stack; pop it, and store it as this frame's return value
- Let _fp_ be current FramePointer info
- pop elements from the stack while _$sp > $fp - (fp.nArgs + fp.nVars)_
- set _$fp = fp.previousFp_
- set _$pc = fp.returningPc_
- push the return value from the first step
