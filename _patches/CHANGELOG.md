Changelog for the Starter Kit Components
========================================

Overview what and why had to be patched in the source code of the components.
The patches are located under the component-specific directories in the current
directory. The changed files are copied to the component-specific directories in
the root directory when `../docker-build.sh` script is executed.


Beacon Network UI
-----------------

Had to install **Python** because NPM failed to install **node-sass**
dependency, which triggered installation from source code using **gyp**.
Maybe the node-sass binary was not compatible with Alpine Linux or maybe it's
an issue related to macOS.


Beacon2 RI API
--------------

`supervisor` is not present in the base image, so it had to be installed.
It is used for launching the application (see `deploy/entrypoint.sh`).
Added `supervisor` as a dependency to `requirements.txt`.


Beacon2 RI Tools
----------------

Changed the base-image to `mongo` so that the MongoDB client tools would
available in the image. In addition, it installs `bcftools` into the image
(previously, `bcftools` was provided through volume and it did not work –
perhaps macOS issue).

Originally, the source code was git-cloned from the GitHub repository. Now it is
copied right from the project directory.


REMS
----

REMS application is built externally, as the default Dockerfile shows that the
JAR-file is actually just copied to the container not built there. Therefore,
the **Dockerfile** and **.dockerignore** files were severely updated to
incorporate building REMS within a container environment.

As **project.clj** previously called git-commands to store references to the
current state of the git-repository, the override file replaces these commands
with hard-coded values. In future, this should be revised. (Note that git is not
installed by default in the container and the **.git** directory is actually
stored in the root folder, so there is no easy way to include it to the image.)

In the **project.clj** file, had to upgrade Java from 8 to 11 because version 8
is no longer supported and it was hard to find good base image for that.

In the **package.json** file, had to freeze the version of **shadow-cljs**
because newer versions broke some dependencies that REMS also depends on.
