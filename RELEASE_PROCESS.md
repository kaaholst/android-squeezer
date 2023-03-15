# Releasing Squeezer

## Create a release branch from the develop branch.

The name of the branch is release-x.y.z, according to the release number.

    git switch -c release-x.y.z develop

## Make 1-n releases from the branch

Repeat the following process for each release. Beta versions are named
x.y.z-beta-n, where n starts at 1.

### Update the version numbers.

Edit `Squeezer/build.gradle`.  Edit the `versionCode` and `versionName`
values.

### Update the release notes.

Edit `Squeezer/src/main/res/xml/changelog_master.xml` with the details.
Run `git log master..develop` to see what's changed

### Update the `produktion.txt` or `beta.txt` release-note files.

Run `./gradlew generateWhatsNew` to update the files.

### Update the `NEWS` file.

Run `./gradlew generateNews` to update the file.

### Generate and test the release APK

From the top level directory, run:

    ./gradlew build
    ./gradlew installRelease

Verify that the version number in the About dialog is correct and that
Squeezer works correctly.

### Update the screenshots (if necessary).

Take new screenshots for market/screenshots.

### Commit the changes

    git commit -a -m "Prepare for release x.y.z."

### Upload to Google Play (beta, and production)

    ./gradlew publishReleaseApk

### Upload to Amazon Appstore

- Go to https://developer.amazon.com/home.html, signed in as
  android.squeezer@gmail.com.

- Find the existing entry for Squeezer, and upload the new APK.

- Include the contents of `production.txt` for this release in the "Recent Changes"
  section.

## Post production-release steps

Carry out the following steps when the production release has been posted,
and the release branch is no longer necessary.

### Merge the changes back to the master branch and tag the release.

    git checkout master
    git merge --no-ff release-x.y.z
    git tag -a x.y.z -m "Code for the x.y.z release."
    git push origin x.y.z

### Merge the changes back to the develop branch.

    git checkout develop
    git merge --no-ff release-x.y.z

### Delete the release branch

    git branch -d release-x.y.z
