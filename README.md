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

## Usage

To start you'll need to have Java 11, a Wikipedia dump and a Internet connection

Start by compiling and packaging everything into one uber-jar
```
./mwnv package
```
Or on Windows,
```
mvnw package
```


Then to convert `mywikidumnp.xml.bz2` to a more compressed format `my_wiki.dump`, run
```
java -jar -x mywikidumnp.xml.bz2 -o my_wiki.dump
```

This will take quite a bit of time, around one to two hours for English Wikipedia.

After you're done with that, you can run 
```
java -jar target/wikilinks.jar -s en_wiki.serialized -i
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

### Unix

On Unixy operating systems, you can supply profile unix to Maven with `./mvnw package -Punix`.

After this, you can replace `java -jar target/wikilinks.jar` with `target/wikilinks`.

## Development

Wikilinks was originally developed to be run as a web service. This added some constraints to memory and cpu usage: CPU
usage should never be high and memory requirements should be small enough to run this in a 2 GB JVM together with a web
server.

This and Java's lack of value-types lead to development of flyweight pages presented by `BufferWikiPage` and
serialization handled by `BufferWikiSerialization`. And inordinate amount of primitive value use + low abstraction. 

This is all needless work and should be by some other mechanism that offers flyweight view into a nicely packed binary
presentation such as [Protocol buffers](https://developers.google.com/protocol-buffers/).

### Tests

To run all tests, enable profile `testAll` which will run tests that take seconds to run.
```
./mvnw test -PtestAll
```

## TODO

Things to improve, clean up, etc

- [ ] Replace own serialization format with Protobuf or similar
- [ ] Drop nowadays unused hierarchies of `LeanWikiPage` and `WikiPage` 
- [ ] Rewrite command line option handling with argparse4j or Kotlin-argparser
- [ ] Move Wikipedia XML -> serialization format to another main
- [ ] Write web interface for querying routes and available articles
- [ ] Port to Bazel or similar build system

