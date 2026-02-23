# Storage Format Options for Wikilinks

## Current state
- `BufferWikiPage` + `BufferWikiSerialization` use a custom binary layout.
- This is fast and compact, but costly to evolve and maintain.

## What to optimize for
- Zero-copy or near-zero-copy reads from a mapped file
- Compact representation for `id + links + title`
- Stable schema evolution (add fields without breaking old data)
- Good Kotlin/JVM support and tooling

## Candidate formats

### 1) FlatBuffers (recommended)
- Pros:
  - Designed for in-place reads from `ByteBuffer`
  - Mature Java/Kotlin support
  - Schema versioning is straightforward
  - Removes most hand-rolled parsing logic
- Cons:
  - Link arrays and strings are still accessed through generated APIs (slightly more indirection)
  - Build adds codegen step (`flatc`)

### 2) Cap'n Proto
- Pros:
  - Very fast zero-copy access model
  - Strong schema evolution model
- Cons:
  - JVM ecosystem is less common than FlatBuffers/Protobuf
  - Integration and team familiarity are usually weaker

### 3) Protobuf
- Pros:
  - Excellent ecosystem and tooling
  - Easy schema evolution
- Cons:
  - Not naturally zero-copy for memory-mapped random access
  - Better for message transport than direct graph mmap workloads

## Suggested schema shape
- `GraphFile`
  - `version: uint32`
  - `page_count: uint32`
  - `pages: [Page]`
- `Page`
  - `internal_id: uint32` (dense, 0..N-1, used for route-finding)
  - `wiki_id: uint64` (original Wikimedia ID; optional but useful for traceability)
  - `flags: uint8` (redirect/article)
  - `title: string`
  - `links: [uint32]` (internal IDs)

## Migration plan
1. Keep current `BufferWikiPage` runtime APIs stable.
2. Add a `FlatBufferWikiSerialization` adapter behind `AbstractSerialization`-like interface.
3. Write both old and new formats behind a feature flag.
4. Add compatibility tests: same input dump => same route answers.
5. Remove old format after one release cycle.

## Why this is a good fit here
- Route-finding already assumes dense in-memory IDs; FlatBuffers fits this model well.
- Existing code can migrate incrementally by adapting construction/lookup layers first.
- You keep memory-mapped performance while dropping manual byte-offset maintenance.
