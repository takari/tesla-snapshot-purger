Tesla Snapshot Purger
=====================

This extension for Tesla helps with the maintenance of your local repository and its disk consumption by automatically
purging old snapshots of an artifact when a newer snapshot of that artifact was downloaded. In other words, the extension
ensures that for any given snapshot artifact at most one timestamped snaphsot version is kept in your local repository.

Keeping just one timestamped snapshot version is fine for most users. However, this kind of purging can be troublesome for
users that occasionally use the anti-pattern of declaring dependencies on specific timestamped snapshots. In this case, the
extension can either be completely uninstalled by deleting it from `${tesla.home}/lib/ext` or it can be configured to exclude
certain artifacts from the purging mechanism. The latter is realized by passing the system property
`tesla.snapshotPurger.excludes` to Tesla, preferably via the `MAVEN_OPTS` environment variable. The value of this system
property is a comma separated list of patterns of the form `groupId:[artifactId]` that denote the artifacts to exclude.
The patterns may use the `*` and `?` characters as wildcards.
