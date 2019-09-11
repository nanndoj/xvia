#!/bin/bash
BASEDIR=$(cd `dirname $0` && pwd)
ARTIFACTORY_USER=deploy
BUILD_DIR=packages/build
REPOSITORY="https://artifactory.xvia.com.br/artifactory/xvia-release"
INSTALL_SRC="packages/src/xroad/installer"

if [ -f VERSION ]; then
    BASE_STRING=$(cat VERSION)
    BASE_LIST=(`echo $BASE_STRING | tr '.' ' '`)
    V_MAJOR=${BASE_LIST[0]}
    V_MINOR=${BASE_LIST[1]}
    V_PATCH=${BASE_LIST[2]}
    echo "Current version : $BASE_STRING"
    V_MINOR=$((V_MINOR + 1))
    V_PATCH=0
    SUGGESTED_VERSION="$V_MAJOR.$V_MINOR.$V_PATCH"
    read -r -p "Enter a version number [$SUGGESTED_VERSION]: " INPUT_STRING
    if [ "$INPUT_STRING" = "" ]; then
        INPUT_STRING=$SUGGESTED_VERSION
    fi
    echo "Will set new version to be $INPUT_STRING"
    echo $INPUT_STRING > VERSION
else
    echo "Could not find a VERSION file"
    read -r -p "Do you want to create a version file and start from scratch? [y]" RESPONSE
    if [ "$RESPONSE" = "" ]; then RESPONSE="y"; fi
    if [ "$RESPONSE" = "Y" ]; then RESPONSE="y"; fi
    if [ "$RESPONSE" = "Yes" ]; then RESPONSE="y"; fi
    if [ "$RESPONSE" = "yes" ]; then RESPONSE="y"; fi
    if [ "$RESPONSE" = "YES" ]; then RESPONSE="y"; fi
    if [ "$RESPONSE" = "y" ]; then
        echo "0.1.0" > VERSION
    fi

fi

V=$(cat VERSION)
read -r -s -p "Enter artifactory password (deploy user): " PSWD

pack () {
  TARGET="xvia-$1$2-v$V.tar.gz"
  echo "$TARGET"
  cp -rf $BASEDIR/$INSTALL_SRC/$1/xvia-install-*.sh $BASEDIR/$BUILD_DIR/$1$2
  mkdir -p "$BASEDIR/$BUILD_DIR/xvia"
  cp -rf "$BASEDIR/$BUILD_DIR/$1$2" "$BASEDIR/$BUILD_DIR/xvia"
  cd "$BASEDIR/$BUILD_DIR" && tar -czvf "$BASEDIR/$BUILD_DIR/$TARGET" "xvia/$1$2"
  rm -rf "$BASEDIR/$BUILD_DIR/xvia"
}

deploy () {
  echo "Deploying $1..."
  curl -u$ARTIFACTORY_USER:$PSWD -T "$BASEDIR/$BUILD_DIR/$1" "$REPOSITORY/$1"
}

echo "Packing ubuntu 14.04..."
TARGET_FILE=$(pack "ubuntu" "14.04")
deploy $TARGET_FILE

echo packing "Packing ubuntu 18.04..."
TARGET_FILE=$(pack "ubuntu" "18.04")
deploy $TARGET_FILE

echo "New version deployed successfully!"
