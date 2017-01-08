Outline of a module file
========================================================================================

A text-based Effes bytecode file (`.efct`) is a semi-human-readable, text-based format for defining an Effes module. Lines are delimited by a single newline (`\n`) character. When this document discusses any line's contents, it does not specify the newline character. It is implied. For instance, if the document specifies a line with contents `foo`, the on-disk representation of that line would actually be `foo\n`.

The first line of an `.efct` file _must_ consist of the following text, exactly:

    efct 0

After that, an `.efct` file may contain zero or more of the following, in any order:

- declarations:
  - class declarations
  - instance methods
  - module functions (i.e., static functions)
- no-ops:
  - comment lines
  - empty lines

Sum types are not declared in module files. They are fully resolved at compile time.


Comments and empty lines
========================================================================================

An empty line is one with no characters. It is a no-op, except when it serves as the marker for a method or function body's end, as described below.

A comment line starts with a hash (`#`). Comment lines are also no-ops.


Declarations
========================================================================================

Class Declarations
----------------------------------------------------------------------------------------

A class declaration creates a simple type. Its name must be unique among all type names in this module. Each declaration is on a single line, and has the format:

    TYPE <name> 0 [constructorArgType, ...]

The `0` is a placeholder for generics.

Each constructor arg is of the form _name:type_, where _name_ is a textual name and _type_ is a type specifier, without spaces.

Examples:

  TYPE 0 Nothing
  TYPE 0 Id id:Long
  TYPE 0 Pet species:Dog|Cat|Bird name:String

Instance method declarations
----------------------------------------------------------------------------------------

Instance methods start with a declaration:

    MTHD <classname> <methodname> 0 <nLocal> <resulType> [argType, ...]

- The classname must correspond to a type declaration contained in this `.efct`, and the methodname must be unique within for a given classname.
- nLocal is the number of local variable slots to request (see `gvar`, `svar` below)
- The `0` is a placeholder for generics.

After the MTHD line, each non-empty line represents an opcode in that function. The method's body ends at the first empty line.

For instance:

    # FriendBuilder : Person.createFriendBuilder(relationship: Friend | Family | Acquaintance)
    MTHD Person FriendBuilder createFriendBuilder Friend|Family|Acquaintance
    <ops...>

Module functions
----------------------------------------------------------------------------------------

Module functions work just like instance methods, with three exceptions:

- `MTHD` becomes `FUNC`
- there is no classname
- the _methodname_ argument is instead called _functionname_

The functionname must be unique within this `.efct` file.

A special case of a function declaration is for `main`. The `main` function must have the following declaration:

    FUNC main 0 Void

This will serve as the entry point for the `.efct`'s execution.

Implicit type declarations
========================================================================================

The following types are implicitly part of any `.efct` file:

    TYPE Long 0
    TYPE Void 0
    TYPE True
    TYPE False
    
The execution stack
========================================================================================

State is maintained on a LIFO stack. Every item in the stack has a type and a value. The type is one of:

- EffesRef
- Long (signed 64 integer)

An EffesRef references an object that has:

- a type
- 0 or more constructor arguments, which can be accessed via the _pvar_ opcode (described below)

Operations are always executed within the context of a method (the one exception to this is the initial invocation of a `main` function to start a program, which is handled by the EVM as a special case. But within that `main` function, operands do have the function context). This context includes the number of arguments that the method has.

ops
========================================================================================

Each op is on a line of its own. The first letters of the line define which op it is, and any arguments are separated by spaces. Multiple or trailing spaces are not allowed.

Many of the operands describe popping elements from the stack. Unless noted otherwise, this results in an error if the stack is empty.

Note that opcodes are only allowed immediately preceding method or function declarations, and all non-comment lines following those declarations until an empty line are opcodes. This means that even if an opcode shared a name with some other declaration marker (for instance, if we had an opcode `mthd`), there would be no ambiguity, as the file's context makes it clear whether a line is an opcode. That said, by convention opcodes are all lowercase, and other markers are all uppercase, so there shouldn't be any ambiguity.

Stack manipulation
----------------------------------------------------------------------------------------

### long _N_

Pushes a Long to the stack. _N_ is a decimal number, which may be negative. for instance, `long 123` or `long -456`.

### pop

Pops and discards the topmost element of the stack.

### parg _N_

Copies the method argument specified by N onto the stack. N is a textual representation of a non-negative integer, 0-indexed. For instance, "parg 0" pushes the first argument to the stack.

Errors if the argument is out of range (that is, is ≥ the number of arguments in the current method).

### gvar _N_

Gets local variable N, and pushes it to the stack. The variable itself is unaffected. This errors if the variable has not been set, or if N is out of range.

### svar _N_

Pops the topmost element from the stack, and sets it to local variable N. Errors if N is out of range.

Branching
----------------------------------------------------------------------------------------

### goto _N_

Moves the program counter to the specified opcode. The opcode is an absolute number, 0-indexed, representing an op in the current method. For instance, `goto 0` goes to the first opcode in the method (ie, the opcode represented by the first line of the method body), `goto 3` goes to the fourth, etc.

### goif _N_

Pops the topmost element, which must be an EffesRef whose type is True or False. Iff the type is True, behaves as the `goto` opcode.

### rtrn

Finishes execution of the current method. The current frame must have exactly one value on the stack, which will then be the method's result. Any other state is an error. If a value is returned, its type is not checked.

Comparisons
----------------------------------------------------------------------------------------

### type typedesc

Pops the topmost item. Pushes a True to the stack iff the item was an EffesRef whose type matched the typedesc, which is something like `List`, `Cat|Dog`, etc. Pushes a False to the stack in all other cases.

### l:lt, l:le, l:eq, l:ge, l:gt

Pops two elements from the stack, which must both be of Long type. The first one popped is the RHS, and the second one is the LHS. Pushes a True if the LHS is less than, less than or equal to, equal to, greater than, or greater than or equal to the RHS (respectively, per opcode). Pushes a False otherwise.

Methods, field access and arithmetic
----------------------------------------------------------------------------------------

### call classname methodname

Calls a function or method.

- If _classname_ is `:`, the call is for a function
- If _methodname_ is the same as _classname_`, this is a constructor call
- Otherwise, the method is a instance method on the class.

This op will pop _n_ args, where _n_ is the number of args the method requires. These are provided in reverse order: the topmost value is the first argument of the method invocation. Instance methods always take at least one argument, and the first argument (that is, the topmost one) must be an EffesRef of the same type that defines the instance method.

This op will push one value to the stack, the method's return value. 

Examples:

- `call Pair Pair` constructs a new Pair instance
- `call : myModuleFunction` calls a function
- `call LinkedList size` calls the `size` method on the `LinkedList` instance represented by the top of the stack.

### pvar name

Pops the topmost item, which must be an EffesRef. Pushes the specified constructor argument (by name) to the top of the stack. Errors if the topmost item is not an EffesRef, or if it is one whose type does not have the requied name.

### ladd, lsub, ldiv, lmul

Adds, subtracts, divides or multiplies two Longs. Pops two elements from the stack, which must both be of Long type. Pushes a Long, which is the result of the operator. In the case of lsub, the first element popped is the divisor, and the second is the numerator.

Other
----------------------------------------------------------------------------------------

### debug-print

Writes a textual representation of the topmost element of the stack to stderr. Does not modify the stack. Does not error if the stack is empty; instead, will print to stderr that the stack is empty.
