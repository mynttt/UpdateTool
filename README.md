# Plex IMDB Rating Update Tool

*Docker mode added but no docker container created yet!*

A tool to update the IMDB ratings for Plex libraries that contain movies.

## What does this do?

#### It is recommended to shut down Plex and creating a backup of your database before using this tool!

#### This tool could in theory break if either the Plex database schema changes or the OMDB API breaks!

The Plex IMDB agent is kind of meh... Sometimes it is not able to retrieve the ratings for newly released movies even tho it matches the IMDB ID with them. It is impossible to comfortable add the rating manually from the users perspective.

This tool allows you to update the database that stores this data with the correct IMDB ratings. It will correct outdated and missing ratings and also set a flag to display the IMDB badge next to the ratings. The source used to retrieve the ratings is the OMDB API.

An advantage is that it works outside Plex by manipulating the local Plex database. Thus, no metadata refresh operations have to be done within Plex. It is faster and will not lead into the unforeseen consequences that one sometimes experiences with a Plex metadata refresh (missing or changed posters if not using a custom poster).

This tool currently only works on movies and will only allow you to select libraries that use the Plex IMDB agent (because it depends on the IMDB ids). In my library with 1800 movies it transformed entries for 698 items.

Before (Not IMDB matched)            |  After Match
:-------------------------:|:-------------------------:
![](img/star.PNG)  |  ![](img/imdb.PNG)

*These are two different movies, that why the genres changed*

This tool supplies two modes at the moment:

### docker mode
Provides a watchdog that once started will run every N hours over all IMDB supported libraries.

### CLI mode (legacy)
Provides a CLI wizard to add and process IMDB update jobs on the supporting libraries.

# Runtime requirements

- Java >= 11

# Created files in PWD

- cache-imdb.json - Cache for Agent
- state-imdb.json - Set of jobs that have not finished
- xml-error-{uuid}-{library}.log - List of files that could not be updated by the XML transform step (not important tbh, plex reads from the DB)
- imdbupdatetool.log - Log file

# Usage

### Docker mode:

In docker mode the tool will read the two environment variables OMDB_API_KEY and PLEX_DATA_DIR.

It can then be invoked with:
- no args (default caching (14 days) and every 12h)
- one arg (default cachning(14 days) and every n hour(s))
- two args (cache purge every n day(s) every n hours(s)) (invoking with 0 days will clear the cache entirely)

```
java -jar ImdbUpdater-xxx.jar imdb-docker [] | [{every_n_hour}] | [{every_n_hour} {cache_purge_in_days}]
```

Example:

```bash
OMDB_API_KEY=abcdefg
PLEX_DATA_DIR="/mnt/user/Plex Media Server"
export OMDB_API_KEY
export PLEX_DATA_DIR

# Default start
java -jar ImdbUpdater-xxx.jar imdb-docker
# Run every 5 hours
java -jar ImdbUpdater-xxx.jar imdb-docker 5
# Run every 12 hours but always purge the cache
java -jar ImdbUpdater-xxx.jar imdb-docker 12 0
```

### Legacy CLI mode:

```bash
java -jar ImdbUpdater-xxx.jar imdb-cli <PlexData> <ApiKey>
```

Parameters
```bash
<PlexData> is the path that points to the Plex Media Server folder which contains folders like Cache, Metadata and Plug-ins
<ApiKey> is an APIKey for the OMDB service https://www.omdbapi.com/

The free option only allows for 1000 requests every 24h. That is not a problem, the tool will halt, persist the state can thus be resumed again when the limit expires. The owner offers a paid 1$ per Month 100000 requests / 24h option that might be attractive to users with larger libraries.
```

Example:

```bash
# Normal mode
java -jar ImdbUpdater-xxx.jar imdb-cli "/mnt/data/Plex Media Server" abcdefg
```

[Where is the data folder of the Plex Media Server located on my system?](https://support.plex.tv/articles/202915258-where-is-the-plex-media-server-data-directory-located/)

You can either build the tool yourself using the command below in the root folder or get it [here](https://github.com/mynttt/PlexImdbUpdateTool/releases/latest) as an already packaged .jar file.
```bash
gradle build
```
