# F-Droid submission

F-Droid builds Encarté from the `vX.Y.Z` tag and signs it with its own key. Texts, images and per-version
changelogs come from `fastlane/metadata/android/` in this repository. After inclusion, new tags are picked up
automatically (`UpdateCheckMode: Tags`, `AutoUpdateMode: Version`), so later releases need no merge request.

## Before submitting

- `v1.0.0` is tagged and pushed, and the `Release` workflow passed on it.
- The repository has `fastlane/metadata/android/en-US/` (title, short and full description), which F-Droid requires.
- `https://encarte.fr` is live (the metadata's `WebSite` points to it).

## Merge request

1. Sign in to GitLab and fork https://gitlab.com/fdroid/fdroiddata.
2. In your fork, create a branch named `io.github.vferries.encarte`.
3. Copy `docs/release/fdroid/io.github.vferries.encarte.yml` to `metadata/io.github.vferries.encarte.yml`, and replace
   `FULL_COMMIT_HASH_OF_TAG_v1.0.0` with the output of `git rev-parse 'v1.0.0^{commit}'` (40 hexadecimal characters).
4. Optional local checks, with fdroidserver installed in a virtualenv (`pip install fdroidserver`), from the fork's
   root: `fdroid readmeta`, `fdroid rewritemeta io.github.vferries.encarte`, `fdroid lint io.github.vferries.encarte`.
   The merge-request pipeline runs the same checks plus a full build.
5. Commit (`New app: Encarté`), push, and open the merge request against `fdroid/fdroiddata` `master` with the
   "App inclusion" template. Tick its checklist: FOSS license, no non-free dependencies (all from Maven Central /
   Google Maven), no tracking, no network permission, the app is yours.
6. Follow the pipeline (checkupdates, lint, build, scanner) and answer the reviewers. Inclusion usually takes a
   few weeks. The app then appears on f-droid.org after the next index publication.

## Known facts for reviewers

- The build is a plain Gradle build of `app/`, with no flavors, no `sudo:` and no prebuilt binaries in the repository apart from `gradle/wrapper/gradle-wrapper.jar`, which F-Droid replaces with
  its own Gradle.
- `io.github.zxing-cpp:android` comes from Maven Central. Other apps in fdroiddata use it as is (Solid Share 3.1.1,
  Chompass 3.1.0).
- The release build is unsigned when no `ENCARTE_UPLOAD_*` variable is set, which is the case on F-Droid's build server.
