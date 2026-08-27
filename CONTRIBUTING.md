# How to contribute

Your contributions to Squeezer are very welcome. Exactly how to do that
depends on what you want to do.

## Reporting bugs and feature requests

Please use the
[issues page](https://github.com/kaaholst/android-squeezer/issues) to
report bugs or suggest new features.

It's appreciated if you take the time to see if someone else has already
reported it, and if so, add a comment to their note.

## Translations

The easiest way to contribute, especially if you are not a programmer,
is to help translate Squeezer's interface in to different languages.

Follow [How to contribute code](#how-to-contribute-code) to fetch the code 
and submit a pull request with your changes. 

For a new translation copy Squeezer/src/main/res/values/strings.xml to a 
folder named Squeezer/src/main/res/values-\<language\>.

The language is defined by a two-letter 
[ISO 639-1](http://www.loc.gov/standards/iso639-2/php/code_list.php) language code, 
optionally followed by a two-letter 
[ISO 3166-1-alpha-2](https://www.iso.org/obp/ui/#iso:pub:PUB500001:en) region code 
(preceded by lowercase r).

Translate the copied file, commit and submit a pull request with your changes.

## Small bug fixes

If you've discovered a bug and want to fix it, or you'd like to have a go
at fixing a bug that's already been reported, please go right ahead.

You can also review the
[list of open bugs](https://github.com/kaaholst/android-squeezer/issues?q=is%3Aopen+is%3Aissue)
if you want inspiration for something to work on.

The [How to contribute code](#How-to-contribute-code) section has technical details on how to contribute code.

## Larger features

Contributing larger features to Squeezer is also very welcome. For these
please use the [issues page](https://github.com/kaaholst/android-squeezer/issues), and let us
know what you plan on working on, so we don't end up duplicating too much
effort.

Please see the [How to contribute code](#How-to-contribute-code) section
for technical details.

## How to contribute code

### Fetch the code

Follow [GitHub's instructions](https://help.github.com/articles/fork-a-repo)
for forking the repository.

### Development cycle

We roughly follow this cycle for new features and bug fixes.

1. Create a feature branch for your feature/bug fix
2. Commit
3. Push
4. Create pull request from feature to develop branches
5. Rebase feature from develop
6. Review
7. Merge feature branch to develop branch using the "fast-forward only"
   merging strategy.

When working on a feature, regularly repeat step 2, 3 and 5. It's ok to force
push to your personal feature branch, as long as you understand the 
implications, and coordinate with your fellow developers.

If possible prefer small incremental commits, this makes it easier to review.
A single pull request may consist of multiple incremental commits.

- A series of commits should tell a story, For example, if you are 
  implementing a feature, which requires a new version of a library, you can
  make a commit to upgrade the library. And then make a commit to use the 
  new feature in the library.
- Commits should generally not undo the work of previous commits in the same
  pull request.
- If you are not comfortable makeing incremental commits, it's okay to begin
  contributing without them.

If you plan a feature, it is important to start the review process early. 
Initially agree on a strategy with the reviewer. Continuously adjust the strategy
together with the reviewer.
Just sending a large bunch of changes in a PR review, does not provide much value,
and often it is too late to do anything about it.
