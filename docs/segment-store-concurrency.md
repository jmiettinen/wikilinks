# SegmentWikiGraphStore concurrency protocol

Context: JMI-43.

`SegmentWikiGraphStore` is opened once per loaded wiki and shared by route/search HTTP requests through `ServerWikiRegistry.LoadedWiki`.

## Construction

- Build or receive a complete `.segment` file before serving it.
- Open it with `SegmentWikiGraphSerialization.open`, which maps graph sections read-only through `FileChannel.map(..., FileChannel.MapMode.READ_ONLY, Arena.ofShared())`.
- Publish the resulting `LoadedWiki` only after construction succeeds.

## Use

- Public read methods on an open store may be called concurrently by multiple request threads.
- `SegmentWikiRoutes` and `WikiSearchIndex` may share the same open store.
- Each `IntCursor` returned by `outNeighbors` or `inNeighbors` is a mutable traversal object. Treat it as single-consumer and do not share a cursor across threads.
- The graph file must not be mutated while mapped and served.

## Destruction

- Stop accepting HTTP requests first.
- Wait for accepted request handlers to finish before closing the registry.
- Close the registry after the HTTP server has quiesced; registry close owns store close.
- Do not call store read methods after close.
- Do not intentionally race `SegmentWikiGraphStore.close()` against in-flight readers. The store has lightweight closed-state checks, but it does not synchronize every graph access with close because that would put lifecycle coordination on the hot path.

`WikiHttpServer.close()` is responsible for the server-side quiescing step before production shutdown hooks close `ServerWikiRegistry`.
