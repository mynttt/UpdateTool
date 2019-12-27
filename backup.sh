#!/bin/bash
#Backup script for XML metadata (only the two info files) and the database

######## Config Begins ########

# Points to the Plex root (.../PlexMediaServer)
# !!! MUST END WITH '/' !!!
PLEX_ROOT="/mnt/user/appdata/PlexMediaServer/"

######## Config Ends ########

SOURCE="${PLEX_ROOT}Library/Application Support/Plex Media Server/Metadata/Movies/"
PLEX_DB="${PLEX_ROOT}Library/Application Support/Plex Media Server/Plug-in Support/Databases/com.plexapp.plugins.library.db"

echo "COPYING MOVIE METADATA (Info.xml x2 + Folder structure) - THIS MIGHT TAKE A WHILE..."
find "$SOURCE" -name "Info.xml"| grep com.plexapp.agents.imdb > metadata_movie_backup_index
while read p; do
    _BASE_DIR=$(echo "$p" | grep -Eo "Movies*.*bundle" | sed 's/Movies//g')
    _BASE_DIR="${SOURCE}${_BASE_DIR}/Contents/"
    _LDIR=$(echo "$p" | grep -Eo "Movies*.*bundle" | sed 's/Movies/metadata_movie_backup/g')
    _LDIR="${_LDIR}/Contents/"
    mkdir -p "$_LDIR/com.plexapp.agents.imdb"
    mkdir -p "$_LDIR/_combined"
    cp "$p" "$_LDIR/com.plexapp.agents.imdb/Info.xml"
    cp "${_BASE_DIR}_combined/Info.xml" "$_LDIR/_combined/Info.xml"
done < metadata_movie_backup_index
7z a metadata_xml_backup.7z metadata_movie_backup
rm -rf metadata_movie_backup
rm metadata_movie_backup_index
echo "COMPLETED"
echo "COPYING PLEX SQLITE DATABASE"
cp "$PLEX_DB" plex_database_backup.db
echo "COMPLETED"