### CONFIGURATION FOR UPDATE TOOL

# BASE CONFIG (required!)

# Point to data dir on local machine
PLEX_DATA_DIR=""
export PLEX_DATA_DIR

RUN_EVERY_N_HOURS="12"
export RUN_EVERY_N_HOURS

# Restart on crash (set to anything else than true to disable)
RESTART_ON_CRASH="true"
export RESTART_ON_CRASH

# Specify max. heap allocatable by the JVM (Larger libraries might need a larger value i.e. 512M / 1G)
JVM_MAX_HEAP="256m"
export JVM_MAX_HEAP

# ADDITIONAL CONFIG 
#
# (Uncomment export to enable)

# TMDB API Key
TMDB_API_KEY=""
#export TMDB_API_KEY

# TVDB API Key 
TVDB_API_KEY=""
#export TVDB_API_KEY

# Opt-in libraries using the new TV show agent
UNLOCK_FOR_NEW_TV_AGENT=""
#export UNLOCK_FOR_NEW_TV_AGENT

# Ignore specific libraries from being processed
IGNORE_LIBS=""
#export IGNORE_LIBS

# Set Capability Flags to be used by the tool
CAPABILITIES=""
#export CAPABILITIES

