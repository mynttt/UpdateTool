#!/bin/sh

set -e

MASTER_URL="https://raw.githubusercontent.com/mynttt/UpdateTool/master/unraid-installer.zip"

echo "***************************"
echo "Welcome to the UpdateTool UnRaid/Linux standalone installer!"
echo "You are executing this script in ${PWD}"
echo "***************************"
echo "The following files/folders will be created:"
echo
echo "- ${PWD}/updatetool/"
echo "- ${PWD}/dependencies/"
echo "- ${PWD}/(install.sh|config.sh|updatetool.sh)"
echo
echo "Are you sure to install this tool here in ${PWD}? [yes/no]"
read x
if [ "$x" != "yes" ]
then
    echo "Installer aborted due to user cancelation."
    exit 1
fi
echo "Downloading installer and dependencies..."
wget --no-cache -O "updatetool-installer.zip" "$MASTER_URL" 2>&1 | grep "^wget:"
unzip "updatetool-installer.zip"
rm "updatetool-installer.zip"
echo "Starting installer... Make sure to follow instructions!"
chmod +x install.sh
chmod +x dependencies/*
./install.sh
echo "Installer has ended."