java_library(
    name = "maven_compile_deps",
    srcs = glob(["src/main/java/**/*.java"]),
    resources = glob(["src/main/resources/**"]),
    visibility = ["//visibility:public"],
    deps = [
        "@maven//:org_apache_commons_commons_compress",
        "@maven//:com_google_code_findbugs_jsr305",
        "@maven//:com_google_guava_guava",
        "@maven//:info_bliki_wiki_bliki_core",
        "@maven//:commons_cli_commons_cli",
        "@maven//:net_openhft_koloboke_api_jdk8",
        "@maven//:net_openhft_koloboke_impl_jdk8",
        ]
)

java_binary(
    name = "wikilinks",
    runtime_deps = ["//:maven_compile_deps"],
    main_class = "fi.eonwe.wikilinks.Main"
)
