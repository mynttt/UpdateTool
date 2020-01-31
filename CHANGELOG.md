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
