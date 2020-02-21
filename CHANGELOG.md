## 1.3.2:
  - removed TMDB notnull assertion as API can return null in rare cases

## 1.3.1:
  - Database sections have been made critical
  - This means that the database is only open once in the beginning per tool iteration and shortly while doing an update (only if necessary)
  - This should only leave the database open for 1-2 seconds per iteration and hopefully eliminate all issues with plex!

## 1.3:
  - Series can now also be updated by utilizing TVDB to resolve IMDB ids
  - TVDB <=> IMDB resolvement only has to be done once
  - Items that fail to be resolved will be blacklisted for 14 days to prevent spamming TVDB on every iteration of the tool
  - TVDB authorization is a bit more complex than simply providing an API key, more about the topic is in the README.md

## 1.2.5:
  - Removed deprecated legacy CLI interface from project
  - Deprecated and removed OMDB interfaces and implementations, this tool will no longer use the OMDB API as IMDB provides a rating dataset that is refreshed daily and thus more up to date

## 1.2.4:
  - In case that the OMDB API fails for some users this tool will now dump some information that could help investigating the issue

## 1.2.3:
  - go back to sleep if the OMDb API fails for some reason instead of aborting execution
  - sleep interval between OMDb API calls that fail
  - better handling of SQLite concurrency by using a custom busy timer and closing all statements immediatly after using them to hopefully prevent BUSY_SNAPSHOT errors while Plex is working on the database.

## 1.2.2:
  - plex locking the database will not cause runtime termination, instead it will be waited until the lock goes away or execution will be canceled if that never happens to prevent endless waiting

## 1.2.1:
  - Tool now allows you to supply a TMDB API key that will try to update IMDB ratings if they are matched with the TMDB agent instead of the IMDB agent.
  - This will only update the rating but not change the agent. If you refresh metadata for that item the IMDB rating will probably be overriden.


## pre 1.2.1:
  - no data available
