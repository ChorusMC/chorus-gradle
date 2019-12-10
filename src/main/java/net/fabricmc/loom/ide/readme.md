### Why do these classes and packages exist?
Loom uses transformers to transform all kinds of artifacts into versions of these artifacts
that match the mappings and game versions.

The way gradles external tooling implementations resolves artifacts of the `sources` and `javadoc`
type via querying for artifacts which does not invoke dependency transformation causing these to not be transformed.

The classes in these package reimplement the tooling for the relevant tools to use dependency based artifact resolution,
which causes the relevant files to be properly transformed.