- module id should be external the efct file; filesystem based, or whatever
- accordingly, Parser.parse should not know about ito
- Linker should be eager (at least for now)

In a `call` op, the single-module format is:

  - `:` for "module function [within this module]"
  - `TypeName` for "instance function on TypeName"

I'm going to ammend that to `module-name:TypeName` where:

  - if _module-name_ is empty, it means "within this module"
  - if _TypeName_ is empty, it means "instance function"

This means that `TypeName` is no longer valid; we need to write `:TypeName`.

Module names consist of one or more module name segments, each of which consists of zero or more characters other than a colon (`:`). Segments are separated by a colon.

Examples:

|| specifier       || meaning                                                      ||
|  :               |  static function in same module                                |
|  :Foo            |  _Foo_ type in same module                                     |
|  bar:            |  static function in _bar_ module                               |
|  bar:Buzz        |  _Buzz_ type in _bar_ module                                   |
|  bar:fizz:Buzz   |  _Buzz_ type in _bar:fizz_ module (a submodule within _bar_)   |
