load("@bazel_tools//tools/build_defs/repo:http.bzl", "http_archive")
load(":junit5.bzl", "junit_jupiter_java_repositories", "junit_platform_java_repositories")

RULES_JVM_EXTERNAL_TAG = "2.8"
RULES_JVM_EXTERNAL_SHA = "79c9850690d7614ecdb72d68394f994fef7534b292c4867ce5e7dec0aa7bdfad"

http_archive(
    name = "rules_jvm_external",
    strip_prefix = "rules_jvm_external-%s" % RULES_JVM_EXTERNAL_TAG,
    sha256 = RULES_JVM_EXTERNAL_SHA,
    url = "https://github.com/bazelbuild/rules_jvm_external/archive/%s.zip" % RULES_JVM_EXTERNAL_TAG,
)

load("@rules_jvm_external//:defs.bzl", "maven_install")

JUNIT_JUPITER_VERSION = "5.5.2"
JUNIT_PLATFORM_VERSION = "1.5.2"
KOLOBOKE_VERSION = "0.6.8"

junit_jupiter_java_repositories(
    version = JUNIT_JUPITER_VERSION,
)

junit_platform_java_repositories(
    version = JUNIT_PLATFORM_VERSION,
)

maven_install(
    artifacts = [
        "org.apache.commons:commons-compress:1.19",
        "com.google.code.findbugs:jsr305:3.0.1",
        "com.google.guava:guava:19.0",
        "info.bliki.wiki:bliki-core:3.1.0",
        "commons-cli:commons-cli:1.3.1",
        "net.openhft:koloboke-api-jdk8:%s" % KOLOBOKE_VERSION,
        "net.openhft:koloboke-impl-jdk8:%s" % KOLOBOKE_VERSION,
        "org.jgrapht:jgrapht-core:0.9.1",
        "org.junit.platform:junit-platform-launcher:%s" % JUNIT_PLATFORM_VERSION,
        "org.junit.jupiter:junit-jupiter-engine:%s" % JUNIT_JUPITER_VERSION,
        "org.junit.vintage:junit-vintage-engine:%s" % JUNIT_JUPITER_VERSION,
        "org.junit.jupiter:junit-jupiter-api:%s" % JUNIT_JUPITER_VERSION,
        "org.hamcrest:hamcrest-core:1.3",
        "junit:junit:4.12"
    ],
    repositories = [
        "https://jcenter.bintray.com/",
        "https://repo1.maven.org/maven2",
    ],
    fetch_sources = True,
    strict_visibility = True,
    maven_install_json = "//:maven_install.json",
)

load("@maven//:defs.bzl", "pinned_maven_install")
pinned_maven_install()

