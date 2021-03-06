## 1.5.6b
- bugfix regarding support for 16 char TVDB v3 keys

## 1.5.6
- support for 16 char TVDB v3 keys

## 1.5.5
- Added webscraper fuctionality with cache (7 days for existing items / 30 day ignore for not rated yet / permanent ignore for 404s) in case the IMDB rating set does not contain the rating for an entry to prevent `Ignoring: 'xyz' with IMDB ID: tt12345 supplies no valid rating := 'null'` situations

## 1.5.4
- Removed spammy messages in resolvement log
- Better handling for mitigation system
- Added mitigation for Plex ICU database change -> **USE ON YOUR OWN RISK UNTIL TESTED FIERCELY BY THE COMMUNITY!**
- Mitigation works by removing two database triggers before updating the ratings and adding them immediatly after again to bypass an SQLite crash due to no ICU extension included in the sqlite-jdbc build
- Bumped id error resolvement caching up to 30 days as most resolvement errors are 404s of dead shows anyways
- Fixed spammy "cached new movie agent xxx id message" that should not appear once an id has been cached

## 1.5.3
- One time mitigation added to combat cache name typo fix via cache reset

## 1.5.2
- TVDB v4 API is now supported. To use it just supply the PIN as the TVDB API key.
- To keep using v3 just use your legacy API key.

## 1.5.1
- TVDB fallback for movies in the new Plex Movie Agent
- Refactoring to support TVDB v4 in a future update

## 1.5.0
- First support for new TV Show Agent (right now only items that have the IMDB ID embedded within the Plex database will be changed -> support for TVDB/TMDB only resolvable items will come soon as for the TVDB resolvable items an update to the new v4 API has to be implemented first).
- This feature is opt-in which means you'll have to add the library IDs to the environment variable `UNLOCK_FOR_NEW_TV_AGENT`. Refer to more information to the main README.
- The opt-in occurs because with Plex it is only possible to switch between TVDB/TMDB but not IMDB. This is a security feature to not wreck the libraries of users that don't want IMDB ratings for their new agent TV Show libraries.
- Updated UpdateTool GUI to reflect changes.

## 1.4.7
- Updated SQLite3 dependency so ARM is supported on mac

## 1.4.6
- Fixed rare NPE

## 1.4.5
- Fixed crash on malformed XML

## 1.4.4
- Disabled XML error logging for non existing metadata XML files as these errors are not important. To re-enable these errors set the capability flag VERBOSE_XML_ERROR_LOG.

## 1.4.3b
- Hotfix for previously unfaced NPE. Added mitigation + logging to futher debug if it happens again with that user.

## 1.4.3
- IMDB id cleaning to minimize the amount of non matches after having an id resolved

## 1.4.2
- tmdb resolve bug fixed

## 1.4.1
- handle edge cases regarding imdb badges

## 1.4.0
- support for new plex movie agent (will only work on libraries that have the IMDB setting set and the external ids downloaded from plex metadata servers after refreshing the library again)

## 1.3.9b
- startup edge case on some systems fixed

## 1.3.9
- Internal changes to support multiple future implementations
- Added GC request call after a batch task completed so memory is freed after every invocation of a task
- Allow specifying JVM max. heap in case that giant libraries crash with the 256M default heap size
- JVM startup options modified so unused heap memory can be released

## 1.3.8
- Database corruption error should be finally fixed now!
- The rare error had something to do with inserting the current time into the changed_at field
- This would cause index index_metadata_items_on_changed_at to corrupt
- I could never reproduce this error, reddit user /u/techno_babble_ sent me logs from reindexing his database that allowed me to finally fix the error!

## 1.3.7
 - Deprecated environment variable TVDB_AUTH_STRING as TVDB API only requires the API Key
 - TVDB_AUTH_STRING can still be used, the API Key will be extracted automatically
 - New variable TVDB_API_KEY introduced which only takes the TVDB API Key

## 1.3.6
- Asynchronous resolvement => faster HTTP processing with APIs
- If TMDB capability exists (TMDB API key set) the tool will also go after movie libraries with the TMDB agent (com.plexapp.agents.themoviedb)
- Previously only libraries with the com.plexapp.agents.imdb were processed, although orphans with the TMDB agents were also resolved.
- This led to confusion and has now been changed to prevent users wondering why a library is being ignored
- Ability to ignore TV and Movie libraries by setting capabilities in the new CAPABILITIES environment variable
- Currently the following CAPABILITIES exist: NO_TV, NO_MOVIE. Both ignore the corresponding libraries.
- CAPABILITIES are configured as a semicolon separated list i.e: CAPABILITIES=NO_TV;NO_MOVIE would render this tool useless.
- This is to allow user configuration for future features.

## 1.3.5
  - TMDB matching for TV shows implemented, TV libraries that use the TMDB agent will now also get their ratings from IMDB as long as a TMDB API key is provided
  - Previously reported wrong rating issue has been fixed with this. The cause of these wrong ratings were older items with a TMDB agent set after the library has been converted to TVDB
  - Plex did not update the agent string for these items after the conversion
  - UpdateTool tried to resolve these items via TVDB as it thought they would all have the TVDB agent

## 1.3.4
  - TVDB API error handling improved by skipping invocation in case the API is unreachable

## 1.3.3
  - added option to ignore libraries via the environment variable IGNORE_LIBS.
  - libraries can be ignored by writing the library ids as a semicolon seperated string into the environment variable
  - Example: Ignoring only #1 -> IGNORE_LIBS=1 | Ignoring #1, #3 and #8 -> IGNORE_LIBS=1;3;8

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
