load("//:junit5.bzl", "java_junit5_test")

java_library(
       name = "tests",
       srcs = glob(["java/**/*.java"]),
       resources = glob(["resources/**"]),
       deps = [
            "//:maven_compile_deps",
            "@maven//:org_apache_commons_commons_compress",
            "@maven//:com_google_guava_guava",
            "@maven//:org_junit_platform_junit_platform_launcher",
            "@maven//:org_hamcrest_hamcrest_core",
            "@maven//:org_junit_jupiter_junit_jupiter_api",
            "@maven//:org_junit_jupiter_junit_jupiter_engine",
            "@maven//:org_junit_vintage_junit_vintage_engine",
            "@maven//:org_jgrapht_jgrapht_core",
            "@maven//:net_openhft_koloboke_api_jdk8",
            "@maven//:junit_junit"
            ],
       exports = [
            "//:maven_compile_deps",
            "@maven//:org_apache_commons_commons_compress",
            "@maven//:com_google_guava_guava",
            "@maven//:org_junit_platform_junit_platform_launcher",
            "@maven//:org_hamcrest_hamcrest_core",
            "@maven//:org_junit_jupiter_junit_jupiter_api",
            "@maven//:org_junit_jupiter_junit_jupiter_engine",
            "@maven//:org_junit_vintage_junit_vintage_engine",
            "@maven//:org_jgrapht_jgrapht_core",
            "@maven//:net_openhft_koloboke_api_jdk8",
            "@maven//:junit_junit"
        ]
)

# Use java_test after bazel officially support junit5
java_junit5_test(
    name = "all_tests",
    srcs = glob([
        "java/**/*.java",
    ]),
    test_package = "fi.eonwe.wikilinks",
    deps = [
         ":tests"
         ]
)

#
#java_test(
#    name = "allTests",
#    runtime_deps = [":tests"],
#    test_class = "fi.eonwe.wikilinks.IntQueueTest"
#)
