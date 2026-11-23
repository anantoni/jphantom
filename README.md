JPhantom
========

A tool for Java program complementation. It takes a single jar as its
main argument and complements it by creating a new jar that adds dummy
implementations for every phantom class detected in the original
one. (A *phantom class* is a class that is referenced somewhere but
its definition is missing.)

The phantom classes in the produced jar will contain every missing
field and method that was referenced and used in the original jar, as
well as a supertype that respects every type constraint that was found
(e.g., if phantom class B was used in a place where known class A was
expected, and a [widening reference
conversion](http://docs.oracle.com/javase/specs/jls/se14/html/jls-5.html#jls-5.1.5)
took place, then we must conclude that class A is a supertype of B).

You can read more about the underlying problem of class hierarchy
complementation in the [OOPSLA '13 paper](http://yanniss.github.io/jphantom-oopsla13.pdf)
(or the [presentation slides](http://gbalats.github.io/assets/presentations/oopsla2013-jphantom.pdf)).


Release
-------

Version 1.4.3 is published through [JitPack](https://jitpack.io/#anantoni/jphantom).
With Gradle:

```
repositories {
  maven { url 'https://jitpack.io' }
}
dependencies {
  implementation 'com.github.anantoni:jphantom:1.4.3'
}
```

Version 1.3 is on Maven Central as `org.clyze:jphantom:1.3`.


Usage
-----

    java -jar <jphantom> <injar>... [--classpath <path>] [--seed <n>] [--debug] [--help] [--save-class-files] [-d <dir>] [-o <outjar>] [-v (--log, --verbose) N]

     <injar>...              : the jar(s) to be complemented
     --classpath (-cp) <path>: existing classes (platform and library jars or class
                               dirs, separated by the path separator), used INSTEAD
                               of the running JVM to decide which referenced classes
                               exist; must include the platform (java.lang.Object)
     --seed <n>              : seed for the arbitrary choices among valid solutions
                               (default 0: deterministic output)
     --lenient               : skip (and report) uses of a missing class that
                               contradict earlier ones, e.g. libraries compiled
                               against incompatible versions of it, instead of failing
     --debug                 : Debug mode
     --help                  : Help
     --save-class-files      : Save phantom class files
     -d <dir>                : Phantom-classes destination directory
     -o <outjar>             : the destination path of the complemented jar
                               (single input jar only)
     -v (--log, --verbose) N : Level of verbosity

### Complementing against the analyzed platform and libraries

By default, a referenced class counts as existing if it is in the input jar or
in the JDK that runs JPhantom. When the jar targets another platform, or uses
libraries, pass them explicitly:

    java -jar <jphantom> app.jar lib1.jar lib2.jar --classpath jre1.8/lib/rt.jar:jre1.8/lib/jce.jar -d phantoms

All input jars are scanned and count as existing; the `--classpath` entries
count as existing but are not scanned; nothing else is consulted. Only the
generated classes are written (to `-d`): exactly the classes that exist
nowhere in the inputs or on the classpath. Existing classes are never modified.

### Changes in 1.4.4

- `--lenient` covers contradictions between present classes, which real
  classpaths contain when libraries were compiled against different versions
  of the same library (found on alfresco, bitbucket-server, dotCMS and
  fineract). Each is skipped and counted with the other inconsistencies:
  - a class that extends an interface, or implements a class, of another jar:
    the contradicting supertype is dropped from JPhantom's model of the
    hierarchy (inputs are never rewritten). The check runs once after all jars
    are loaded, in class-name order, instead of while loading, where it fired
    only if the supertype happened to be loaded first;
  - a subtype constraint on a present class whose supertypes are all known and
    do not include the target;
  - a constraint that marks a present type with the other kind (class vs
    interface): the declaration wins.
- The missing members of a phantom class are added by one adapter per class.
  Before, every use of a member chained one more adapter, and a heavily used
  phantom class overflowed the stack.

### Changes in 1.4.3

- Method references and other constants: classes and members referenced only
  through a `MethodHandle` (e.g. `Foo::bar`), a `MethodType` or a dynamic
  constant, in `ldc` or in the bootstrap arguments of `invokedynamic`, are
  complemented like those of field and method instructions. Before, a missing
  class used only by a method reference was not generated.
- `package-info` classes are complemented like other classes (the types of
  package annotations are generated when missing). `module-info` classes and
  multi-release copies under `META-INF/` are still skipped.

### Changes in 1.4.2

- No stack overflow on heavily used missing annotations (duplicate access
  adapters are no longer chained).

### Changes in 1.4.1

- Modern class files: ASM 9.8 (class files up to Java 25), ASM9 API (e.g. sealed
  classes no longer fail).
- Java 8 interface methods: `invokespecial`/`invokestatic` on an interface (a
  default method's super call, a static interface method) imply an interface
  owner and generate a non-abstract interface method with a stub body (in a
  Java 8 class file; other phantom classes keep their version).
- `--lenient`: real classpaths mix libraries built against different versions of
  a missing (or existing) library, so no single complement satisfies every use.
  Strict mode (default) fails as before; `--lenient` keeps the first
  interpretation, skips the contradicting use, and reports the number skipped.

### Changes in 1.4

- `--classpath`: decide existing classes (existence, supertypes, members) from
  explicit jars instead of the running JVM. Before, JPhantom generated JDK
  classes the target platform has but the running JDK lacks, skipped the
  reverse, generated library classes unless the input was a fat jar, and failed
  on JDK 15+ (its ASM could not read the running JVM's `java.lang.Object`).
- Several input jars.
- Deterministic output (`--seed`, default 0); before, two runs on the same
  input could produce different phantom classes.
- Jars with `module-info.class` (and multi-release jars) no longer crash it.

You can use the `--save-class-files` option to save the generated
class files for the phantom classes that were detected in the
process. This is handy when you want to inspect only the code that was
autogenerated, without having to extract the relevant class files from
the complemented jar.

You can also increase the level of verbosity to log every constraint
that was detected by using the `--verbose N` option.
