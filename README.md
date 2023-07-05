Local Deployment of the GDI Starter Kit
=======================================


The Purpose
-----------

1. Links to the source code of the main components in GDI (via **Git Modules**).
2. Scripts for building and running the components (via **Docker Compose**)
3. Identify configuration changes in order to make the components work together.
4. At the moment, this project concerns only the Starter Kit, and not production.
5. At the moment, this project concerns only deployment (no development here).


The Structure
-------------

### Special Directories

* `_config/` – app-specific configuration files that are referenced in the
  docker-compose files.
* `_volumes/` – folders that are mounted to some docker containers as referenced
  in the docker-compose files.
* `_volumes/_infra/` – infrastructure and storage specific directories.
* `_patches/` – our customisations to some linked repositories that are
  applied automatically when the `docker-build.sh` script is executed.

> **NOTE:** Please distinguish configuration changes from patches!
> Custom configuration should go to `_config/` not `_patches/`.
>
> Although many images include default configuration in the image, we should
> not specify customisations in the image itself.
> Instead, custom configuration files are injected through `configs` in  the
> **docker-compose.yml** file.


### Linked Projects

This project includes the **master** or **main** branches of the repositories of
the starter kit components. Please refer to the `.gitmodules` file for details.

Having these projects linked as submodules, it is possible to actually build
from the source code and easier to debug application behaviour.

Use this command to add a new submodule (replace _master_, if necessary, and
_repository URL_):

```shell
git submodule add -b master git@github.com:org/repo.git
```

Unfortunately each project has each own standards and also problems. To solve
these issues, the **_patches/** directory contains override-files with custom
fixes per project, which are copied when the `docker-build.sh` script is called.

To update these projects, execute following in the root directory of the
deployment project:

```shell
# Revert changes:
git submodule foreach 'git restore .'

# Pull remote updates:
git submodule update --remote

# Rebuild (might fail due to changes and custom overrides)
./docker-build.sh
```

At the moment, we avoid committing changes to the original repositories.


Getting Started
---------------

Initially, when cloning the main project, the submodules are not fetched (unless
`git clone --recursive ...` was used). When the submodule directories are empty,
please check out the code for all submodules using this:


```shell
git submodule update --init
```

Next, build all the GDI-specific images. The script also copies override-files
and downloads huge files for Beacon to `_volumes/beacon/` (only when the
directory is still empty).

```shell
./docker-build.sh
```

Launching should be performed in two steps:
1. the storage (database, mongo, minio)
2. GDI starter kit applications

```shell
# The storage containers:
docker-compose -f docker-compose-storage.yml up -d

# The OIDC container:
docker-compose -f docker-compose-oidc.yml up -d

# The GDI components in docker-compose.yml:
docker-compose up
```

The localhost port-mappings are documented inside **docker-compose*.yml** files
(in the beginning of the files).


Clean And Remove
----------------

Before any cleaning operations, the applications must be shut down first:

```shell
docker-compose -f docker-compose.yml -f docker-compose-storage.yml down
```

### Volumes

Use `docker volume ls` command to view created volumes (which all have prefix
`local-deployment_`), and `docker volume rm <volume_name>` command to remove a
specific volume. The volume will be automatically recreated when applications
are launched again.


### Containers

Use `docker-compose rm` command to remove created containers:

```shell
# Remove specific container:
docker-compose rm rems

# Remove all containers specified in given file:
docker-compose -f docker-compose-storage.yml rm
```

### Docker

Over time the disk space used by Docker will grow and should be occasionally
cleaned:

```shell
# View consumed space:
docker system df

# Remove stopped containers:
docker container prune

# Remove dangling images:
docker image prune

# All previous:
docker system prune

# More extreme: unused tagged images and also volumes that are not in use
docker system prune -a --volumes
```
