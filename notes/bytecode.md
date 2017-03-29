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

    FUNC <functionscope> <functionname> <nArgs> <hasRv> 0

- functionscope defines a scope for this function, as described below. The functionname must be unique within for a given classname.
- nArgs is the number of arguments this function takes
- hasRv must be 0 or 1. 0 means there is no return value, and 1 means there is one
- The `0` is a placeholder for generics.

After the FUNC line, each non-empty line represents an opcode in that function. The function's body ends at the first empty line.

The functionscope is of the form _module:Type_:

- _module_ is a module name, or an empty string to specify "this module" (the one defined in this efct file). A module name consists of one or more module name segments, each of which consists of one or more characters other than a colon (`:`), delimited by colons.
- _Type_ is a type name, or an empty string to specify a module-level ("static") function

For instance:

- `foo:` means a module-level function in _foo_
- `foo:bar:` means a module-level function in _foo:bar_, a sub-module of _foo_
- `foo:Buzz` means an instance-level function on the _Buzz_ type, in the _foo_ module
` `:Buzz` means an instance-level functoin on the _Buzz_ type in the current module
- `:` means a module-level function in the current module

For instance methods, the 0th arg is implicitly the instance on which the method is invoked, and the method will actually take `nArgs + 1` arguments. For instance, `FUNC :Dog eat 2 0 0 0` will actually take 3 args: a `Dog` reference and the two declared arguments.

A function's body can reference local variables, either by pushing them to its local stack or by popping the local stack to a variable. A function will always have at least _nArgs_ variables, which will be initialized to the values that the caller passes to the function. variable 0 is the first arg passed in, variable 1 is the second, and so on. Variables are untyped. There is no difference from the variables used to store arguments and any other local variables, other than their initialization; non-arg local variables are uninitialized, meaning that trying to push them to the local stack will result in an error. In particular, they share the same index space. For instance, if a method declares 3 arguments and uses additional 2 local variables, it will have 5 local variables at its disposal. The first three (indexes _0_, _1_ and _2_) will initially contain the arguments' values, in order; the first "local" variable will actually have index _3_.

A special case of a function declaration is for the module function `main`. The `main` function must have the following declaration:

    FUNC : main 1 1 0

This will serve as the entry point for the `.efct`'s execution. Instance functions may also be named `main`, and these have no relation to the module function `main`. The argument to the module function `main` is an Array representing the program's arguments. The return return variable is an Integer representing the program's exit code.

The execution stack
========================================================================================

State is maintained on a LIFO stack.

An EffesObject references an object that has:

- a type
- 0 or more constructor arguments, which can be accessed via the _pfld_ and _sfld_ opcodes (described below)

Operations are always executed within the context of a function (the one exception to this is the initial invocation of a `main` function to start a program, which is handled by the EVM as a special case. But within that `main` function, operands do have the function context). This context includes the number of arguments that the function has.

ops
========================================================================================

Each op is on a line of its own. The first letters of the line define which op it is, and any arguments are separated by spaces. Multiple or trailing spaces are not allowed.

Many of the operands describe popping elements from the stack. Unless noted otherwise, this results in an error if the local stack is empty. Operands can also peek at the stack. This is functionally equivalent to a pop immediately followed by a push.

Note that opcodes are only allowed immediately preceding function declarations, and all non-comment lines following those declarations until an empty line are opcodes. This means that even if an opcode shared a name with some other declaration marker (for instance, if we had an opcode `mthd`), there would be no ambiguity, as the file's context makes it clear whether a line is an opcode. That said, by convention opcodes are all lowercase, and other markers are all uppercase, so there shouldn't be any ambiguity.

Description conventions
----------------------------------------------------------------------------------------

The following opcodes have the following format:

> `opcode` _arg0_ ... _argN_ @pop0 ... @popM -> pushVar

That represents an operation that:

- is identified as `opcode`
- takes N arguments (within the efct line)
- pops M elements from the stack.
- pushes `pushVar` to the stack

All elements other than the opcode name itself are optional in the opcode's description. If they are mentioned in the description, they are _not_ optional when invoking the opcode unless they're surrounded by square brackets.

As mentioned in the [vm-stack][vm-stack] document, elements are described in caller-order, which is the reverse order by which the opcode will see them as it pops them off the stack.

For instance, consider an opcode descibed as:

    regx _where_ @lookIn @pattern -> match

This opcode takes one argument in the `.efct` file, _where_, which for sake of argument we'll say must be either "anywhere" or "full", describing essentially whether to use Java's `Matcher.find` or `Matcher.matches` when executing the regular expression. It will also pop two values off the stack. The first value to be popped is `@pattern` (_not_ `@lookIn`). It will evaluate the regular expression, and then push the match result to the stack.

The opcode might be invoked as:

    str  "foo(.*)" # push the string to the stack
    pvar 0         # pushes local arg 0 to the stack
    regx full      # test whether local arg 0 matches "foo(.*)" in full

[vm-stack]: vm-stack.md

Ops on native types
----------------------------------------------------------------------------------------

Native function calls ("native" in the sense of being handled directly by the EVM) are specified as a separate opcode per call.

These all follow the convention of `call_<type>:<function>`. For instance, `call_String:regex` calls the native `regex` function for String types.

This convention lets the underscore match up with the spaces following 4-char opcodes, such that things line up and generally look nice:

    pvar 3
    call : myfunction
    pstr "hello world"
    pstr ".*"
    call_String:regex

Stack manipulation
----------------------------------------------------------------------------------------

### pop @ignored

Pops and discards the topmost element of the stack.

### pvar _N_ -> varValue

Gets local variable N, and pushes it to the stack. The variable itself is unaffected. This errors if the variable has not been set, or if N is out of range.

N is a textual representation of a non-negative integer, 0-indexed. For instance, "pvar 0" pushes the first variable to the stack.

### svar _N_ @varValue

Pops the topmost element from the stack, and stores it in local variable N. Errors if N is out of range.

Branching
----------------------------------------------------------------------------------------

### labl _name_

Defines a label, with a name that must be unique within this function. goto, goif and gofi can jump to these named labels instead of to an absolute position. There are no restrictions on labels, other than that they cannot be an integer value, as the various go* ops will treat such values as absolute jump locations instead of named labels. The unenforced convention is to use lower_snake_case, prefixed with an @ sight: `@my_jump_loc`, for instance.

### goto _N_

Moves the program counter to the specified opcode. The operand may either be a number or a named label in this function. If it is a number, it represents an absolute opcode index 0-based, representing an op in the current function. For instance, `goto 0` goes to the first opcode in the function (ie, the opcode represented by the first line of the function body), `goto 3` goes to the fourth, etc. If it is a string, it must correspond to a label defined by a labl op in this function.

### goif _N_ @condition

Pops the topmost element, which must be an EffesRef whose type is True or False. Iff the type is True, behaves as the `goto` opcode.

### gofi _N_ @condition

Pops the topmost element, which must be a True or False. Iff the type is False, behaves as the `goto` opcode

### rtrn

Finishes execution of the current function. The current frame must have exactly one value on the stack, which will then be the function's result. Any other state is an error. If a value is returned, its type is not checked.

Functions, field access and object manipulation
----------------------------------------------------------------------------------------

### type typedesc @item -> isOfRightType

Pops the topemost item. Pushes a True to the stack if its type matches the typedesc, which is something like `Cat`. Pushes a False otherwise.

### typp

Works like `type`, except that it only peeks for the top item (instead of popping it).

### typf

(type+if) Works like `type`, except that the value is popped only if it does not match. In other words, if the stack started with `[item]`, then after this operation it will either be `[item, True]` or `[False]`.

### call functionscope functionname [@arg0 ... arg@1] -> result

Calls a function.

- If _functionscope_ is as defined above, in the _Function Declarations_ sections
- If _functionscope_ defines a type scope, and functionname is the type name (e.g. `:Person Person`), this is a constructor call

This op will pop _n_ args, where _n_ is the number of args the function requires. These are provided in reverse order: the topmost value is the first argument of the function invocation. Instance functions always take at least one argument, and the first argument (that is, the topmost one) must be an EffesRef of the same type that defines the instance function.

This op will push one value to the stack, the function's return value. 

Examples:

- `call :Pair Pair` constructs a new Pair instance
- `call : myModuleFunction` calls a static function
- `call :LinkedList size` calls the `size` function on the `LinkedList` instance represented by the top of the stack.

### pfld typename fieldname @effesItem -> effesItemField

Pops the topmost item, which must be an EffesRef of type _typename_. Pushes the specified constructor argument (by name) to the top of the stack. Errors if the topmost item is not an EffesRef of the right type, or if that type does not have a field by that name.

_typename_ is a functionscope, as defined above, which must have a type name

### sfld typename fieldname @effesItem @effesItemField

Pops an EffesRef of type _typename_ and a value, and sets the specified constructor argument (by name) to the value. Errors if the topmost item is not an EffesRef of the right type, or if that type does not have a field by that name.

### call_native:toString @obj -> string

Pops the topmost item, which must be a built-in type (ie, not an EffesRef). Pushes its toString representation as a String.

Array operations
----------------------------------------------------------------------------------------

### arry @size -> array

Creates an array of size `@size`.

### call_Array:store @idx @value

Pops an array, an Integer, and an object of unchecked type. Stores the `@value` reference into the array at the specified index.

### call_Array:get @idx -> value

Pops an array and an Integer, and pushes the array value at that index to the stack. If no value has been stored at that index, pushes a `False`.

### call_Array:len -> length

Pops an array, and pushes its length as an Integer.

Boolean operations
----------------------------------------------------------------------------------------

### bool str -> boolean

Pushes a True or False to the stack. `str` must be one of `True` or `False` (case sensitive).

### call_Boolean:negate @value -> negation

Pops an element from the stack, which must be True or False, and pushes its negation (False or True, respectively).

### call_Boolean:and / or / xor

Pops two Booleans from the stack, and pushes the result of the logical boolean operation.

Integer operations
----------------------------------------------------------------------------------------

### int _N_ -> N

Pushes an Integer to the stack. _N_ is a decimal number, which may be negative. for instance, `int 123` or `int -456`.

### call_Integer:parse @sVal -> iVal

Pops a String from the stack and tries to parse it as an Integer. Pushes the parsed Integer or False if the parsing failed.

Parsing is done via Java's `Integer.parseInt`

### call_Integer:&lt;cmp&gt;

_&lt;cmp&gt;_ is one of: lt, le, eq, ge, gt

Pops two elements from the stack, which must both be of Integer type. Pushes a True if @lhs is less than, less than or equal to, equal to, greater than, or greater than or equal to @rhs (respectively, per opcode). Pushes a False otherwise.

### call_Integer:&lt;op&gt; @lhs @rhs -> result

_&lt;op&gt;_ is one of: add, sub, div, mult

Adds, subtracts, divides or multiplies two Integers. Pops two elements from the stack, which must both be of Integer type. Pushes a Integer, which is the result of the operator.

Match operations
----------------------------------------------------------------------------------------

### call_Match:igroup @match @captureIdx -> string

Pops a Matcher from the top of the stack. Pushes its Nth group to the stack, or False if the group didn't match.

### call_Match:ngroup @match @captureName -> string

Pops a Matcher from the top of the stack. Pushes the captured group _name_ to the stack, or False if the group didn't match.

### call_Match:groupCount @match -> int

Pops a Matcher from the top of the stack. Pushes the number of captured groups it has, including named captured groups.

String operations
----------------------------------------------------------------------------------------

### str _value_ -> value

Pushes a String of type _value_ to the stack.

### call_String:len @str -> len

Pops an element from the stack, which must be a String. Pushes an Integer representing its length (in number of code points, _not_ in terms of 2-byte chars as Java naturally does) to the stack.

### call_String:regex @lookIn @pattern -> didMatch

Pops two elements from the stack, a pattern and a string to search for. Pushes a False if the pattern does not match, or a Match object if it does.


### call_String:sout @string

Pops a string from the stack and writes it to stdout. No newline is added.

Stream operations
----------------------------------------------------------------------------------------

### call_Stream:stdin -> Stream

Pushes a StreamIn object representing stdin.`

### call_Stream:readFile @file -> Stream

Opens a file for reading, and pushes its stream to the stack.

### call_Stream:readLine @stream -> string

Pops a StreamIn from the stack. Reads a line from it, and push either that line as a String (without the trailing newline) or False if no more lines are available.

### call_Stream:stdinLine -> string

Shortcut for:

    call_Stream:stdin
    call_Stream:readLine

This method only exists historical/convenience purposes.

Other
----------------------------------------------------------------------------------------

### debug-print

Writes a textual representation of the topmost element of the local stack to stderr. Does not modify the stack. Does not error if the local stack is empty; instead, will print to stderr that it is empty.

### fail _message_

Prints information about the VM's current state, and then exits with the given message.
