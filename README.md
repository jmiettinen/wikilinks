# Wikilinks

Wikilinks builds a compact graph from Wikimedia XML dumps and uses it to find
routes between Wikipedia articles. It can run as:

- a converter from Wikimedia XML dumps to `.segment` graph files
- an interactive command-line route query tool
- a local HTTP server with a small browser UI and JSON API
- a Docker image that serves prebuilt wiki graph files

The current default storage format is `segment`. The older `buffer` format is
still accepted by some commands for compatibility, but new graphs should use
`segment`.

## Prerequisites

- Java 25. The Gradle wrapper will use the configured Java toolchain.
- Docker, only if you build the runtime images.
- A Wikimedia dump in `pages-meta-current` XML bzip2 format. Small test fixtures
  are checked in under `src/test/resources/`; real dumps should go under `data/`,
  which is ignored by Git.

Wikimedia dumps are listed at <https://dumps.wikimedia.org/backup-index.html>.
For route finding, use "All pages, current versions only". Large wikis may be
split across multiple `.xml.bz2` files; keep the parts for one wiki in their own
directory before conversion.

## Build And Test

```bash
./gradlew build
./gradlew test
```

`build` creates the runnable shaded jar at `build/libs/wikilinks.jar`.

## Convert A Wiki

The preferred conversion path is the Gradle task, because it is repeatable and
will replace the configured output file if it already exists:

```bash
./gradlew convertWiki \
  -PwikiXmlInput=src/test/resources/szlwiki-2026-06-01-p2p87807.xml.bz2 \
  -PwikiBinaryOutput=build/wikis/szlwiki.segment
```

For a real single-file dump:

```bash
./gradlew convertWiki \
  -PwikiXmlInput=data/fiwiki-2026-06-01-p1p1908609.xml.bz2 \
  -PwikiBinaryOutput=build/wikis/fiwiki.segment \
  -PwikiConvertMaxHeap=6g
```

For a split dump, pass the directory containing only that wiki's content parts:

```bash
./gradlew convertWiki \
  -PwikiXmlInput=data/enwiki-2026-06-01-content-bzip2 \
  -PwikiBinaryOutput=build/wikis/enwiki.segment \
  -PwikiConvertMaxHeap=24g
```

Supported conversion properties:

- `wikiXmlInput`: input `.xml.bz2` file or directory of `.xml.bz2` parts. Default:
  `data/wiki.xml.bz2`.
- `wikiBinaryOutput`: output graph file. Default: `build/wikis/wiki.segment`.
- `wikiInputFormat`: `xml`, `buffer`, or `segment`. Default: `xml`.
- `wikiOutputFormat`: `segment` or `buffer`. Default: `segment`.
- `wikiIndexInput`: optional multistream index `.txt.bz2` for a single bzip2 XML
  input.
- `wikiNoIndex`: set to `true` to disable index usage for bzip2 XML input.
- `wikiConvertMaxHeap`: conversion JVM heap. Default: `4g`.

The raw CLI is also available:

```bash
java -jar build/libs/wikilinks.jar convert \
  --input data/wiki.xml.bz2 \
  --input-format xml \
  --output build/wikis/wiki.segment \
  --output-format segment
```

The CLI refuses to overwrite an existing output file.

## Run The Server Locally

First build a `.segment` file. The checked-in Silesian fixture is a quick smoke
test that does not require downloading a full wiki:

```bash
./gradlew build
./gradlew convertWiki \
  -PwikiXmlInput=src/test/resources/szlwiki-2026-06-01-p2p87807.xml.bz2 \
  -PwikiBinaryOutput=build/wikis/szlwiki.segment
```

Then start the HTTP server:

```bash
java -jar build/libs/wikilinks.jar server \
  --port 8080 \
  --max-in-flight-routes 8 \
  szlwiki build/wikis/szlwiki.segment
```

Open <http://localhost:8080/> for the browser UI.

The server accepts repeated `<wiki-name> <segment-file>` pairs, so multiple
wikis can be served from one process:

```bash
java -jar build/libs/wikilinks.jar server \
  fiwiki build/wikis/fiwiki.segment \
  enwiki build/wikis/enwiki.segment
```

Useful endpoints:

```text
GET /health/live
GET /health/ready
GET /api/wikis
GET /api/{wiki}/search?q=Finland&limit=20
GET /api/{wiki}/route?from=Finland&to=Helsinki&neighborhood=10
```

`neighborhood` controls how many outgoing neighbor nodes are included around the
route for UI visualization. The default is `0`; the maximum accepted value is
`1000`.

## Interactive CLI Query

To query a graph in the terminal:

```bash
java -jar build/libs/wikilinks.jar query \
  --input build/wikis/szlwiki.segment \
  --input-format segment
```

The prompt asks for a start article and an end article. Enter `<` for a random
article or `#` for a wildcard.

## Docker

Build a runtime image after one or more `.segment` files exist under
`build/wikis/`:

```bash
./gradlew dockerBuildWikiImage -PwikiDockerImage=wikilinks:local
```

Run it locally:

```bash
docker run --rm -p 8080:8080 wikilinks:local
```

The image bundles `build/libs/wikilinks.jar` and `build/wikis/*.segment`. On
startup it serves every bundled segment file using the filename stem as the wiki
name, exposes port `8080`, and honors:

- `PORT`: server port inside the container. Default: `8080`.
- `JAVA_OPTS`: additional JVM options.
- `MAX_IN_FLIGHT_ROUTES`: concurrent `/route` request limit. Default: `1`.

To build the linux/amd64 GraalVM-based image:

```bash
./gradlew dockerBuildGraalVmX64WikiImage \
  -PwikiGraalVmDockerImage=wikilinks:graalvm-x64
```

## Project Notes

- [Segment store concurrency](docs/segment-store-concurrency.md)
- [Storage format options](docs/storage-format-options.md)
