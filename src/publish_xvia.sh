#!/bin/bash

ACCESS_TOKEN="9bea0c3f-4de7-460f-9092-422709e8b372"
DIR="$( cd "$( dirname "${BASH_SOURCE[0]}" )" >/dev/null 2>&1 && pwd )"
BUILD_DIR="${DIR}/packages/build"
DIST_FOLDER=xvia
STAGING_DIR="${BUILD_DIR}/${DIST_FOLDER}"
VERSION= "${VERSION_NUM:-6.23.0}"

publish () {
  echo "Cleaning staging folder"
  rm -rf "${STAGING_DIR}"

  echo "Preparing staging files for $1"
  mkdir -p "${STAGING_DIR}"

  if [ $1 == "redhat" ]
  then
    cp -R "${BUILD_DIR}/xroad/$1/RPMS/x86_64/*.rpm" "${STAGING_DIR}/$1"
  else
    cp -R "${BUILD_DIR}/$1" "${STAGING_DIR}"
  fi

  cp "${DIR}/packages/src/xroad/installer/$1/install-centralserver.sh" "${STAGING_DIR}/$1"
  cp "${DIR}/packages/src/xroad/installer/$1/install-securityserver.sh" "${STAGING_DIR}/$1"

  cd "$BUILD_DIR";
  tar -czvf "xvia-$1-v${VERSION}.tar.gz" "${DIST_FOLDER}/$1"

  echo "Pushing to artifactory"
  curl --insecure --progress-bar -u deploy:Extreme@1 -X PUT "https://artifactory.xvia.com.br/artifactory/xvia-release/" -T "xvia-$1-v${VERSION}.tar.gz"
}

publish "${DIST:-ubuntu18.04}"
