#!/bin/bash

SCRIPT_DIR="$( dirname -- "$BASH_SOURCE"; )";
cd "${SCRIPT_DIR}"

set -e

echo "Welcome to the UpdateTool for localized UnRaid Environments Installer."

echo -e "\nAre you sure to install this in:\n\n'${SCRIPT_DIR}/updatetool'?\nRunning this installer again will reset the instasllation.\n[yes/no]"
read x
if [ "$x" != "yes" ]
then
    echo "Installer aborted!"
    exit 1
fi

echo "Deleting old UpdateTool installation..."
rm -rf updatetool
rm -f config.sh updatetool.sh
mkdir -p updatetool
cd updatetool

echo "Installing UpdateTool in a localized environment..."

echo "Downloading JDK 17..."
wget https://download.java.net/java/GA/jdk17.0.2/dfd4a8d0985749f896bed50d7138ee7f/8/GPL/openjdk-17.0.2_linux-x64_bin.tar.gz

echo "Extracting JDK 17..."
tar xvf openjdk-17.0.2_linux-x64_bin.tar.gz
mv jdk-17.0.2 jdk

echo "Removing JDK 17 archive..."
rm openjdk-17.0.2_linux-x64_bin.tar.gz

echo "First time setting up local Plex SQLite3 Binary..."
../dependencies/bootstrap_plex_binary.sh

echo "First time bootstrapping UpdateTool Jar..."
../dependencies/bootstrap_updatetool_jar.sh

echo "Copying config/controlscript..."
cd ..
cp dependencies/config_default.sh config.sh
cp dependencies/updatetool.sh .

echo "*******************************"
echo "INSTALLATION DONE"
echo "*******************************"