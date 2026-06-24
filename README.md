# io.github.htanwar922.eclipse.clangformat

Eclipse plug-in with two independent features:

1. **Format with clang-format** - runs an external `clang-format` binary
   on the active file (or selection), configurable from Preferences.
2. **Toggle Aligned Line Comment** - a `//`-comment toggle that places the
   marker at the text, not at column 0, and aligns it to the *lowest*
   indentation level across a multi-line selection.

Both work in any `ITextEditor` (so the CDT C/C++ editor, but also the
generic text editor) - there's no hard CDT dependency.

> **Naming note:** the project bundles two unrelated editing features, so
> if you want a more accurate name, something like
> `io.github.htanwar922.eclipse.cdttools` reads better than "clangformat".
> To rename: change `Bundle-SymbolicName` in `META-INF/MANIFEST.MF`,
> `Activator.PLUGIN_ID`, the `<name>` in `.project`, the project folder
> name, and (optionally) the Java package / command ID prefixes via a
> find-and-replace on `io.github.htanwar922.eclipse.clangformat`.

## Project layout

```
io.github.htanwar922.eclipse.clangformat/
├── META-INF/MANIFEST.MF
├── plugin.xml
├── build.properties
├── .project / .classpath / .settings/
└── src/io/github/htanwar922/eclipse/clangformat/
    ├── Activator.java
    ├── core/
    │   ├── ClangFormatRunner.java      external process invocation
    │   └── CommentToggler.java         alignment algorithm (no UI deps)
    ├── handlers/
    │   ├── FormatWithClangFormatHandler.java
    │   └── ToggleAlignedCommentHandler.java
    └── preferences/
        ├── PreferenceConstants.java
        ├── PreferenceInitializer.java
        └── ClangFormatPreferencePage.java
```

## Requirements

- Eclipse with the **Plug-in Development Environment (PDE)** feature
  installed (e.g. "Eclipse IDE for RCP and RAP Developers", or PDE added
  on top of "Eclipse IDE for C/C++ Developers" via Install New Software).
- A `clang-format` binary on disk or PATH.

## Import & run

1. `File > Import > Existing Projects into Workspace`, point at this
   folder.
2. `Run As > Eclipse Application` to launch a runtime instance with the
   plug-in loaded, for testing.

## Export / install into your real Eclipse

`File > Export > Deployable plug-ins and fragments`, select this
project, export to a directory, then drop the resulting `plugins/*.jar`
into your Eclipse installation's `dropins/` folder (create it next to
`plugins/` if it doesn't exist) and restart.

## Configuration

`Window > Preferences > Clang-Format`:

- **Clang-format executable path** - absolute path, or just
  `clang-format` if it's on PATH.
- **Additional arguments** - defaults to `-style=file` (uses the
  nearest `.clang-format`). Anything clang-format accepts works here,
  e.g. `-style=Google`.
- **Format entire file when there is no text selection** - on by
  default; uncheck to format only the current line when nothing is
  selected.

## Default keybindings

| Command                       | Default       |
|--------------------------------|---------------|
| Format with clang-format       | `Ctrl+Alt+F`  |
| Toggle Aligned Line Comment    | `Ctrl+/`  |

(`Cmd+Option+...` on macOS.) Both are scoped to the "C/C++ Editing"
context, so they only fire with a text editor focused. Rebind via
`Window > Preferences > General > Keys` if either clashes with something
on your setup.

Both commands also show up in the **Edit** menu and the editor's
right-click context menu.

## How "Toggle Aligned Line Comment" works

Default Eclipse comment toggling slams `//` at column 0. This one
inserts `// ` right at the text instead, and for multi-line selections,
aligns every `//` to the **shallowest indentation in the selection** -
deeper lines keep their relative indentation after the marker.

**Single line**, cursor anywhere on it:

```
    int x = compute();
```
toggles to
```
    // int x = compute();
```

**Selecting a whole block** (mixed indentation, min = 0):

```
if (cond) {
    doA();
    doB();
}
```
toggles to
```
// if (cond) {
//     doA();
//     doB();
// }
```
Notice `doA()`/`doB()` keep their 4-space indent relative to the `//`,
they just now sit after it instead of before it.

**Selecting only the inner two lines** (min = 4, both same indent):

```
    doA();
    doB();
```
toggles to
```
    // doA();
    // doB();
```

Toggling again on any of the above restores the original text exactly
(it strips `//` plus one following space, nothing else).

**Rules for mixed/edge cases:**
- Blank (whitespace-only) lines are always skipped entirely - not
  commented, not used when computing the minimum indentation.
- If the selection has a mix of commented and uncommented lines, the
  action is "comment": **every** non-blank line gets a fresh `// `
  prepended, including ones that already start with `//` - they end up
  double-marked (e.g. `// // foo();`), matching VS Code. Toggling again
  strips exactly one `//` layer per line, so a double-marked line is
  restored to its original single comment rather than losing it.
- Uncomment only triggers when *every* non-blank selected line is
  already commented.
- Indentation is measured in raw characters (tabs count as 1, same as
  spaces). This is correct as long as a given block uses one consistent
  indent style, which is the normal case.

## How "Format with clang-format" works

- No selection (and "format whole file" is checked): the whole document
  is piped through `clang-format` on stdin and the buffer is replaced
  with the result.
- With a selection: `-lines=<start>:<end>` is passed so only those lines
  are reformatted (clang-format still returns the whole file; the rest
  is passed through unchanged).
- `-assume-filename=<name>` is passed using the editor's actual file
  name, so clang-format picks the right language/style rules.
- Runs in a background `Job` so the UI doesn't block; on failure (bad
  path, non-zero exit, parse error) you get a dialog with
  `clang-format`'s stderr instead of a silently mangled buffer.

### `.clang-format` existence check

If the configured arguments use (or default to) `-style=file`, the
plug-in searches for a governing `.clang-format` before changing
anything, in this order:

1. the edited file's own directory, then every parent directory above
   it (this naturally passes through the project folder in the normal
   case);
2. the file's **project base directory**, checked explicitly even if
   step 1 didn't reach it (e.g. the file is a linked resource living
   outside the project's real location on disk);
3. the **workspace root**, checked explicitly even if the project
   itself lives outside the workspace (an external project location).

The first `.clang-format` found anywhere in that chain wins. If none is
found at all, **nothing is formatted** - you get a warning dialog
instead, so you don't end up with a huge diff from an unintended
fallback style.

Once found, the plug-in rewrites the args to `-style=file:<absolute
path>` before invoking clang-format, and also runs the process with its
working directory set to the file's own folder. This is deliberate:
clang-format's *own* `-style=file` search is based on the spawned
process's current working directory, not on the edited file's location -
so without this, it could silently search the wrong directory tree
depending on how Eclipse itself was launched. Resolving the path
ourselves makes it deterministic.

If you've set an explicit non-file style (e.g. `-style=LLVM`,
`-style=Google`, or an inline `-style="{...}"`) in Preferences, this
check is skipped entirely, since clang-format won't consult a
`.clang-format` file in that case anyway.
