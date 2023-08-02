# Star Ring

Like a Dyson Sphere, a mega-structure of packages around Pulsar

## Subprojects

Star Ring is made of a bunch of subprojects - each one of them is a specific package, but
they can be bundled together to form a better editor experience on Pulsar

### Generic LSP

A generic LSP client that connects to any LSP server (supported and, in the future, even
servers that are not officially supported can be used) and offers autocomplete, etc.

Status: Beta, usable. Needs Linter and Intentions for better experience.

### Star Linter

A Linter UI for the Pulsar's package Linter.

Status: In development

### VS Pulsar

A VSCode API over Pulsar one.

Status: Not working yet

## Developing

Because of the way ClojureScript works, we unfortunately can't have two or more
Pulsar packages in "watch" or "development" mode loaded at the same time because
they conflict with each other, with weird errors that are quite hard (close to
impossible) to understand. So if you _do have_ other packages in ClojureScript,
be sure to compile them on "release" mode (basically - avoid leaking the global
`goog` variable).

What you'll need to do:

1. Identify the Pulsar packages' directory (usually, `/home/your-user/.pulsar/packages`)
2. Clone this repository somewhere (let's say, `/home/your-user/projects/star-ring`)
3. Symlink the packages you want to develop, plus `star-ring`, to your packages
directory
4. Install dependencies with `npm install`
5. Start the watch process with `npx shadow-cljs watch package`
6. If you have Pulsar open, reload it

So, if your home folder is in `/home/myself`, and you want to develop
`star-linter` and `generic-lsp`, you can do it by:

```bash
ln -s /home/myself/projects/star-ring/star-linter \
  /home/myself/projects/star-ring/generic-lsp \
  /home/myself/projects/star-ring/star-ring \
  /home/myself/.pulsar/packages

cd /home/myself/projects/star-ring/
npm install
npx shadow-cljs watch package
```
