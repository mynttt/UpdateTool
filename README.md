# Plex IMDB Rating Update Tool

A tool to update the IMDB ratings for Plex libraries that contain movies.

![](img/dbmode.gif)

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

There are two modes:
- DB Mode: will only update the database.
- Normal Mode: will update the database and also update all the Info.xml files that are in the <hash>.bundle folders in the Metadata/Movie folder. These files have no impact on the database so updating them is not important. I don't know what they do I guess it is a fallback option or something like that.

# Runtime requirements

- Java >= 11

# Usage

- Normal mode:

```bash
java -jar ImdbUpdater-xxx.jar <PlexData> <ApiKey>
```

- DB mode:

```bash
java -jar ImdbUpdater-xxx.jar dbmode <DbPath> <ApiKey>
```

```bash
# Parameters
<PlexData> is the path that points to the Plex Media Server folder which contains folders like Cache, Metadata and Plug-ins
<DbPath> is the database that plex uses usually named com.plexapp.plugins.library.db
<ApiKey> is an APIKey for the OMDB service https://www.omdbapi.com/

The free option only allows for 1000 requests every 24h. That is not a problem, the tool will halt, persist the state can thus be resumed again when the limit expires. The owner offers a paid 1$ per Month 100000 requests / 24h option that might be attractive to users with larger libraries.
```

[Where is the data folder of the Plex Media Server located on my system?](https://support.plex.tv/articles/202915258-where-is-the-plex-media-server-data-directory-located/)

You can either build the tool yourself using the command below in the root folder or get it [here](https://github.com/mynttt/PlexImdbUpdateTool/releases/tag/1.0.2) as an already packaged .jar file.
```bash
gradle build
```
