# Wikilinks

## Introduction

Wikilinks is a command-line tool to find shortest paths between two articles in Wikipedia.

It was supposed to be a web service with a frontend, but so far I've been more interested in optimizing
path finding than developing a useful service.

In order to do that, it chews in wikimedia XML dumps, spits out a more condensed version of the article graph and uses
that to do the queries. This transforms ~100 GB uncompressed English wikipedia to ~900 MB of titles and links which
can then read quickly and queried.

You can use also Wikipedias in other languages than English. They're quicker to get started with
too.

Wiki dumps are listed [here](https://dumps.wikimedia.org/backup-index.html) and for path finding to function properly,
you'll need to fetch all the pages.

For example, for Breton language, you might go to [it's dump page](https://dumps.wikimedia.org/brwiki/20191101/)
and there select ["All pages, current versions only"](https://dumps.wikimedia.org/brwiki/20191101/brwiki-20191101-pages-meta-current.xml.bz2).

## Building

This project uses Gradle with included wrapper.
```bash
./gradlew build
```

## Usage

After you've compiled the code, we'll start by reading Wikimedia XML files and converting them to a more compact format.

To convert `mywikidumnp.xml.bz2` to a more compact format `my_wiki.dump`, run
```
java -jar build/libs/wikilinks.jar convert --input mywikidump.xml.bz2 --input-format xml --output data/mywikidump.segment --output-format=segment 
```

This will take quite a bit of time, around one to two hours for English Wikipedia.

After you're done with that, you can run 
```
java -jar build/libs/wikilinks.jar query --input data/mywikidump.segment --input-format segment 
```
to start the interactive querying.

You'll eventually get a prompt asking for a start article
```
Please type the starting article ('<' for random article and '#' for wildcard)>
```
Typing `Foobar` there will take you to the next prompt
```
Please type the end article ('<' for random article and '#' for wildcard)>
```
Typing `Finland` there will give you route between those two articles.
For some version on Wikipedia, you'll get
```
Route: "Foobar" -> "World War II" -> "Central Powers" -> "Finland" (in 29 ms)
```

Inputting `<` to the prompt will give you a random page.

###

## Development

Wikilinks was originally developed to be run as a web service. This added some constraints to memory and cpu usage: CPU
usage should never be high and memory requirements should be small enough to run this in a 2 GB JVM together with a web
server.

This and Java's lack of value-types lead to development of flyweight pages presented by `BufferWikiPage` and
serialization handled by `BufferWikiSerialization`. And inordinate amount of primitive value use + low abstraction. 

This is all needless work and should be by some other mechanism that offers flyweight view into a nicely packed binary
presentation such as FlatBuffers.

### Tests

```bash
./gradlew test
```

## TODO

Things to improve, clean up, etc

- [ ] Replace own serialization format with FlatBuffers or similar
- [ ] Drop nowadays unused hierarchies of `LeanWikiPage` and `WikiPage` 
- [X] Rewrite command line option handling with argparse4j or Kotlin-argparser
- [ ] Move Wikipedia XML -> serialization format to another main
- [ ] Write web interface for querying routes and available articles
- [X] Port to Bazel or similar build system

