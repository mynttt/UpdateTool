## 1.7.0
- Removed transactions from native SQLite binary interaction + using batch mode on binary to hopefully mitigate further SQLite corruption issues
- Added `PRINT_SQLITE_BINARY_EXECUTE_STATEMENTS` capability to further diagnose future potential SQLite statement/corruption issues
- Deprecated all code of now defunct TVDB v3 API
- Optimization of TVDB Show lookup efficiency
- Removed deprecated und useless XML file update feature
- Due to UnRaid/Docker inter-communication suspected database corruptions an installer to run UpdateTool bare metal on UnRaid has been created (Readme will be updated @ 15.02.2023 18:00 CET)

## 1.6.6
- Fixed broken ImdbScraper due to changes on IMDBs website.
- Updated the IMDB resolvement process to now factor in both TVDB/TMDB instead of just choosing one of them. This is in preparation to hopefully soon support IMDB lookup for items that only have a TMDB ID from the new Plex agent.

## 1.6.5
- Updated ImdbScraper to handle new IMDB web design. The scraper will now work again instead of throwing tons of `appears to not be allowed to be rated by anyone` messages.
- Mitigation added to automatically reset the set of scraper blacklisted items for older versions once on start-up, so you don't need to wait for 30 days until the scraper picks up those possibly wrongly blacklisted items again for processing.
- Changed internal expiration values for Scraper: `Refresh ratings => 7 days`, `Blacklist if unrated => 30 days` and `Blacklist if forbidden to be rated => 90 days` 

## 1.6.4
- New capabilities flag `DISABLE_SCREEN_SCRAPE` that allows to disable screen scraping if it causes problems (mainly timeouts and 503 requests which cause an unsuccessful and extremely slow metadata resolvement).
- Metadata resolvement will now give absolute and relative processed count updates via the log in case a lookup session takes longer than one minute. This is so large libraries do not give the impression of the tool having a hang-up. Example: `Current meta data resolvement status: [27399/46386] (59,07 %) - Next update in 1 minute.`
- More memory performant query building for native SQL binary usage via lazy loading iterator.
- Fixed bug where a malformed SQL query causes the tool to halt indefinitely. The tool will now exit and output the malformed queries for further diagnoses.
- Quick saves for metadata json files so progress will be saved after a crash/premature exit by user.

## 1.6.3b
- fixed "database not found" bug that some docker users experienced and could only solve it via a `OVERRIDE_DATABASE_LOCATION` entry

## 1.6.3
- Added capabilities `IGNORE_NO_MATCHING_RESOLVER_LOG` (Supresses printing items that have no matching resolver to the log) and `IGNORE_SCRAPER_NO_RESULT_LOG` (Supresses printing web scraper no-match results that either have no rating on the IMDB website or are not allowed to be rated by anyone on the IMDB website and thus will never have ratings).
- Bumped web scraper re-scanning to 14 days

## 1.6.2
- Bumped Plex SQLite native binary abortion timeout to avoid false alarms on weaker systems

## 1.6.1
- Added capability `DONT_THROW_ON_ENCODING_ERROR` that can be used to prevent forced shutdown if extra data of an item causes decoding errors.

## 1.6.0
- Bumped scraping blacklist expiry from 30 to 90 days
- Introduced `USE_PLEX_SQLITE_BINARY_FOR_WRITE_ACCESS` environment variable that allows to use Plex's own non-standard SQLite3 version for write operations. This should fix any kind of database corruptions that occurred mysteriously for some users. In non-docker environments, this must point to the Plex specific SQLite binary (contained in the Main Plex Folder next to the Plex Media Server binary). In docker environments this just needs to contain `true` in order to be used as the docker contains a local and stripped down version of Plex for this purpose.

## 1.5.8
- Added `OVERRIDE_DATABASE_LOCATION` environment variable in case that the `Plex Media Folder/Plug-in Support/Databases/*.db` folder structure is violated. (Folder needs to contain the database file tho! You can't change the name of that (yet)).

## 1.5.7
- Database corruption mitigation reworked in order to support newer plex versions
- Added some library processing details for easier debugging of potential future issues

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
