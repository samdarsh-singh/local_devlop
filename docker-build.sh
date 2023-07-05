#!/usr/bin/env bash
set -eu

# Switch to the directory of this script:
pushd "$(dirname $0)" > /dev/null


# This is a fix for the issue that beacon2-ri-tools contains "beacon" and
# "BEACON" in the same folder, which macOS filesystem does not accept.
if [ ! -d beacon2-ri-tools/BEACON ]; then
  pushd beacon2-ri-tools/ > /dev/null
  git mv beacon beacon2
  git restore BEACON
  popd > /dev/null
fi


# Overwrite project directories with our customisations:
cp -rf _patches/* .
rm CHANGELOG.md

# Copy REMS migrations (only *.up.sql files) to a single file:
cat rems/resources/migrations/*.up.sql > _volumes/_infra/postgres/rems/migrations.sql
# Fix: lines containing just the ending parenthesis without semicomma (adds it)
sed -i '' -e 's/^)$/);/' _volumes/_infra/postgres/rems/migrations.sql

# Copy SDA and Beacon-Network to PG database initialisation scripts:
cp -f sensitive-data-archive/postgresql/initdb.d/* _volumes/_infra/postgres/sda/
cp -f beacon-network/registry/db/docker-entrypoint-initdb.d/init.sql _volumes/_infra/postgres/beacon-network/ddl.sql

# Copy Beacon2 test-data and initialisation script for MongoDB:
cp -f beacon2-ri-api/deploy/data/*.json _volumes/_infra/mongo/data/
cp -f beacon2-ri-api/deploy/mongo-init/init.js _volumes/_infra/mongo/define-collections.js


# Download Beacon2 databases and tools (if not present yet):
if [ ! -d _volumes/beacon/databases/ ]; then
  mkdir -p _volumes/beacon
  pushd _volumes/beacon/ > /dev/null

  echo 'Directory [_volumes/beacon/databases/] does not exist locally.'
  do_download=
  while true; do
      read -p "Do you wish to download the databases? [yN] " yn
      case $yn in
          [Yy]* ) do_download="true"; break;;
          [Nn]* | "" ) do_download=; break;;
      esac
  done

  if [ $do_download ]; then
    rm -f beacon2_data.* || true
    echo
    echo "Beginning to download databases and tools for Beacon..."

    set -x
    wget ftp://FTPuser:FTPusersPassword@xfer13.crg.eu:221/beacon2_data.md5
    wget ftp://FTPuser:FTPusersPassword@xfer13.crg.eu:221/beacon2_data.part1
    wget ftp://FTPuser:FTPusersPassword@xfer13.crg.eu:221/beacon2_data.part2
    wget ftp://FTPuser:FTPusersPassword@xfer13.crg.eu:221/beacon2_data.part3
    wget ftp://FTPuser:FTPusersPassword@xfer13.crg.eu:221/beacon2_data.part4
    wget ftp://FTPuser:FTPusersPassword@xfer13.crg.eu:221/beacon2_data.part5

    md5sum beacon2_data.part? > my_beacon2_data.md5
    set +x

    # This should provide empty output as checksums are not different:
    diff=$(diff my_beacon2_data.md5 beacon2_data.md5)
    if [ -n "$diff" ]; then
      echo '[ERROR] md5sums of downloaded files does not match expected md5sum:'
      echo "$diff"
      exit 1
    fi

    set -x
    # Join the files into a single tar.gz:
    cat beacon2_data.part? > beacon2_data.tar.gz
    rm beacon2_data.part?

    # Extract the data:
    tar -xvf beacon2_data.tar.gz

    # Create a missing symlink:
    cd databases/snpeff/v5.0 && ln -s GRCh38.99 hg38
    set +x

    popd > /dev/null
  fi
fi


echo 'File-system updates completed. Starting the build with docker-compose.'
export BUILDKIT_PROGRESS=plain
docker-compose build
echo 'Build completed.'

popd > /dev/null
