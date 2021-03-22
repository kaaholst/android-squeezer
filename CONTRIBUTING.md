# How to contribute

Your contributions to Squeezer are very welcome. Exactly how to do that
depends on what you want to do.

## Reporting bugs and feature requests

Please use the
[issues page](https://github.com/nikclayton/android-squeezer/issues) to
report bugs or suggest new features.

It's appreciated if you take the time to see if someone else has already
reported it, and if so, add a comment to their note.

## Translations

The easiest way to contribute, especially if you are not a programmer,
is to help translate Squeezer's interface in to different languages.

There's more information on how to do that at
[Translating Squeezer](https://github.com/nikclayton/android-squeezer/wiki/Translating-Squeezer).

## Small bug fixes

If you've discovered a bug and want to fix it, or you'd like to have a go
at fixing a bug that's already been reported, please go right ahead.

You can also review the
[list of open bugs](https://github.com/nikclayton/android-squeezer/issues?utf8=%E2%9C%93&q=is%3Aissue+is%3Aopen+label%3Abug)
if you want inspiration for something to work on.

Please see the [Co-ordination](#Co-ordination) section if you think the
fix is going to be particularly complex, or if it touches lots of
files. The [How to contribute code](#How-to-contribute-code) section
has technical details on how to contribute code.

## Larger features

Contributing larger features to Squeezer is also very welcome. For these
please review the [Co-ordination](#Co-ordination) section, and let us
know what you plan on working on, so we don't end up duplicating too much
effort.

Please see the [How to contribute code](#How-to-contribute-code) section
for technical details.

## Co-ordination

The old mailing list at android-squeezer@googlegroups.com is not longer monitored.
All issues are migrated to Github and should be discussed there.

## How to contribute code

This guide assumes you have already downloaded and installed the Android
SDK, in to a directory referred to as $SDK.

### Fetch the code

Follow [GitHub's instructions](https://help.github.com/articles/fork-a-repo)
for forking the repository.

### Checkout the code

We (roughly) follow the branching model laid out in the
[A successful Git branching model](http://nvie.com/posts/a-successful-git-branching-model/)
blog post.

Specifically:

*   The `master` branch is generally kept pristine. No development work
    happens here.

*   The `develop` branch is for small bug fixes or other cleanups that need
    no more than a single commit to complete.

*   All other work happens on separate branches, branched from `develop`.

*   When those branches are complete and ready for release they are merged on
    `develop`.

*   New releases are prepared by creating a release branch from `develop` and
    working there, before merging changes from the release back to `develop`
    and `master`.

### Starting work

Suppose you want to start work on contributing a new feature. After fetching
the repository checkout a new local branch from develop with:

    git checkout -b new-branch-name develop

Then work on `new-branch-name`, pushing it up to GitHub as appropriate. Feel
free to have discussions on the mailing list as you're working.

### Keeping up to date with `develop`

As you're working other changes may be happening on the origin `develop`
branch. Please use `git rebase` to pull in changes from `develop` to ensure
your branch is up to date and that the future merge back in to `develop` is
straightforward. To do that (assuming you have no open changes on your
current branch):

```
git checkout develop  # Checkout the develop branch
git pull              # Fetch the most recent code
git checkout -        # Checkout the previous branch
git rebase develop    # Rebase current branch from develop
```

### Android Studio configuration

*   Run Android Studio

*   If you have no projects open then choose "Import project" from the dialog
    that appears.

    If you already have a project open then choose File > Import Project...

*   In the "Select File or Directory to Import" dialog that appears, navigate
    to the directory that you fetched the Squeezer source code in ("android-squeezer").

