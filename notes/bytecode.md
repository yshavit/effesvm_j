Outline of a module file
========================================================================================

A text-based Effes bytecode file (`.efct`) is a semi-human-readable, text-based format for defining an Effes module. 

An `.efct` file may contain zero or more of the following, in any order:

- declarations:
  - class declarations
  - functions
- empty lines

Sum types are not declared in module files. They are fully resolved at compile time.

File format
----------------------------------------------------------------------------------------

An `.efct` file is a UTF-8 encoded text file that consists of one or more lines, each of which consist of zero or more tokens. Lines are delimited by a single newline (`\n`) character. When this document discusses any line's contents, those contents do not inlude the trailing newline character. For instance, if the document specifies a line with contents `foo`, the on-disk representation of that line would be `foo\n`.

Within each line, all tokens are strings. If certain opcodes or declarations require non-string operands, the onus is on them (not the `.efct` format parser) to parse the strings.

Tokens are separated by whitespace, as defined by Java's `Character.isWhitespace(int)` function. Tokens have two forms:

- bareword tokens, consiting of one or more non-whitespace, non-hash (`#`) characters (e.g. `foo`)
- quoted tokens, consisting of a double quote (`"`), followed by zero or more characters other than a newline or non-escaped quote, followed by a closing double quoted (e.g. `"foo bar"`)

For both kinds of tokens, the following escape sequences are allowed:

- `\b`, `\t`, `\n`, `\f`, `\r`, `\"`, `\'`, or `\\`, as defined in [JLS 3.10.6][jls-3.10.6]
- `\uXXXX`, where XXXX is a four-digit hexadecimal representing a _single_ code point
- `\u{...}`, where `...` is an arbitrary-length hexadecimal representing a single code point

Note that unlike in Java, higher-plane (supplementary) code points cannot be represented as two `\uXXXX` escapes. For instance, `\uD83D\uDE80` does not represent the rocket character. You should instead use the `\u{...}` form for these code points, as in `\u{1F680}`.

[jls-3.10.6]: http://docs.oracle.com/javase/specs/jls/se8/html/jls-3.html#jls-3.10.6

The first line of an `.efct` file _must_ consist of the following tokens, exactly:

    efct 0

A hash (`#`) not within a quoted token begins a comment. It and the rest of the line are ignored.
    
An empty line is one with no tokens. It is a no-op, except when it serves as the marker for a function body's end, as described below.

Declarations
========================================================================================

Class Declarations
----------------------------------------------------------------------------------------

A class declaration creates a simple type. Its name must be unique among all type names in this module. Each declaration is on a single line, and has the format:

    TYPE <name> 0 [constructorArg, ...]

The `0` is a placeholder for generics.

Each constructor arg is specified by name.

Examples:

  TYPE 0 Nothing
  TYPE 0 Id id
  TYPE 0 Pet species legCount

Function declarations
----------------------------------------------------------------------------------------

Functions start with a declaration:

    FUNC <classname> <functionname> 0 <nLocal> <nArgs>

- The classname must correspond to a type declaration contained in this `.efct` (or `:`, as noted below), and the functionname must be unique within for a given classname.
- nLocal is the number of local variable slots to request (see `pvar`, `svar` below)
- nArgs is the number of arguments this function takes
- The `0` is a placeholder for generics.

After the FUNC line, each non-empty line represents an opcode in that function. The function's body ends at the first empty line.

For instance:

    # FriendBuilder : Person.createFriendBuilder(relationship: Friend | Family | Acquaintance)
    FUNC Person FriendBuilder createFriendBuilder Friend|Family|Acquaintance
    <ops...>

If the classname is `:`, this defines a module function.

A special case of a function declaration is for the module function `main`. The `main` function must have the following declaration:

    FUNC : main 0 <nLocal> 0

This will serve as the entry point for the `.efct`'s execution. Instance functions may also be named `main`, and these have no relation to the module function `main`.

The execution stack
========================================================================================

State is maintained on a LIFO stack. Every item in the stack has a type and a value. The type is one of:

- EffesRef
- Boolean
- Integer (signed 64 integer)

An EffesRef references an object that has:

- a type
- 0 or more constructor arguments, which can be accessed via the _pvar_ opcode (described below)

Operations are always executed within the context of a function (the one exception to this is the initial invocation of a `main` function to start a program, which is handled by the EVM as a special case. But within that `main` function, operands do have the function context). This context includes the number of arguments that the function has.

ops
========================================================================================

Each op is on a line of its own. The first letters of the line define which op it is, and any arguments are separated by spaces. Multiple or trailing spaces are not allowed.

Many of the operands describe popping elements from the stack. Unless noted otherwise, this results in an error if the local stack is empty.

Note that opcodes are only allowed immediately preceding function declarations, and all non-comment lines following those declarations until an empty line are opcodes. This means that even if an opcode shared a name with some other declaration marker (for instance, if we had an opcode `mthd`), there would be no ambiguity, as the file's context makes it clear whether a line is an opcode. That said, by convention opcodes are all lowercase, and other markers are all uppercase, so there shouldn't be any ambiguity.

Stack manipulation
----------------------------------------------------------------------------------------

### pop

Pops and discards the topmost element of the stack.

### parg _N_

Copies the function argument specified by N onto the stack. N is a textual representation of a non-negative integer, 0-indexed. For instance, "parg 0" pushes the first argument to the stack.

Errors if the argument is out of range (that is, is ≥ the number of arguments in the current function).

### pvar _N_

Gets local variable N, and pushes it to the stack. The variable itself is unaffected. This errors if the variable has not been set, or if N is out of range.

### svar _N_

Pops the topmost element from the stack, and stores it in local variable N. Errors if N is out of range.

Branching
----------------------------------------------------------------------------------------

### goto _N_

Moves the program counter to the specified opcode. The opcode is an absolute number, 0-indexed, representing an op in the current function. For instance, `goto 0` goes to the first opcode in the function (ie, the opcode represented by the first line of the function body), `goto 3` goes to the fourth, etc.

### goif _N_

Pops the topmost element, which must be an EffesRef whose type is True or False. Iff the type is True, behaves as the `goto` opcode.

### rtrn

Finishes execution of the current function. The current frame must have exactly one value on the stack, which will then be the function's result. Any other state is an error. If a value is returned, its type is not checked.

Functions, field access and object manipulation
----------------------------------------------------------------------------------------

### type typedesc

Pops the topmost item. Pushes a True to the stack iff the item was an EffesRef whose type matched the typedesc, which is something like `List`, `Cat|Dog`, etc. Pushes a False to the stack in all other cases.

### call classname functionname

Calls a function.

- If _classname_ is `:`, the call is for a module function
- Otherwise, if _functionname_ is the same as _classname_`, this is a constructor call
- Otherwise, the function is a instance method on the class.

This op will pop _n_ args, where _n_ is the number of args the function requires. These are provided in reverse order: the topmost value is the first argument of the function invocation. Instance functions always take at least one argument, and the first argument (that is, the topmost one) must be an EffesRef of the same type that defines the instance function.

This op will push one value to the stack, the function's return value. 

Examples:

- `call Pair Pair` constructs a new Pair instance
- `call : myModuleFunction` calls a function
- `call LinkedList size` calls the `size` function on the `LinkedList` instance represented by the top of the stack.

### pvar name

Pops the topmost item, which must be an EffesRef. Pushes the specified constructor argument (by name) to the top of the stack. Errors if the topmost item is not an EffesRef, or if it is one whose type does not have the requied name.

Integer operations
----------------------------------------------------------------------------------------

### int _N_

Pushes an Integer to the stack. _N_ is a decimal number, which may be negative. for instance, `int 123` or `int -456`.

### i:lt, i:le, i:eq, i:ge, i:gt

Pops two elements from the stack, which must both be of Integer type. The first one popped is the RHS, and the second one is the LHS. Pushes a True if the LHS is less than, less than or equal to, equal to, greater than, or greater than or equal to the RHS (respectively, per opcode). Pushes a False otherwise.

### iadd, isub, idiv, imul

Adds, subtracts, divides or multiplies two Integers. Pops two elements from the stack, which must both be of Integer type. Pushes a Integer, which is the result of the operator. In the case of lsub and idiv, the first element is the RHS and the second is the LHS.

String operations
----------------------------------------------------------------------------------------

### str _value_

Pushes a String of type _value_ to the stack.

### call_String:len

Pops an element from the stack, which must be a String. Pushes an Integer representing its length (in number of code points, _not_ in terms of 2-byte chars as Java naturally does) to the stack.

### call_String:regex

Pops two elements from the stack, a pattern and a string to search for. Pushes a False if the pattern does not match, or True if it does.

Other
----------------------------------------------------------------------------------------

### debug-print

Writes a textual representation of the topmost element of the local stack to stderr. Does not modify the stack. Does not error if the local stack is empty; instead, will print to stderr that it is empty.
