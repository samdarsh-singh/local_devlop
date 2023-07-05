# Beacon v2 Tools and Beacon v2 API

This repository contains the necessary files and instructions for setting up and running Beacon v2 Tools and Beacon v2 API. The Beacon v2 Tools project focuses on generating data in Beacon Friendly Format (BFF) from other data formats (XLSX, VCF) and uploading the data directly to the MongoDB. The Beacon v2 API is a server implementation that allows querying genomic data using the GA4GH Beacon API.

## Prerequisites

- Docker Desktop: You can download and install Docker Desktop for Mac from the [Docker website](https://www.docker.com/products/docker-desktop) or using brew (`brew install --cask docker`). Once you have installed Docker Desktop, make sure it is running by checking the Docker icon in the top menu bar.
- Docker Compose, which can be installed using brew: `brew install docker-compose`
- Optional: HTTPie, which can be installed using brew: `brew install httpie`
- Optional: jq, which can be installed using brew: `brew install jq`
- Optional: md5sum, which can be installed using brew: `brew install coreutils`

## Repository Structure

- Dockerfile: Dockerfile to build the Beacon v2 Tools container.
- README.md: This README file.
- config.yaml: Configuration file for the Beacon v2 Tools container.
- beacon-db.yml: Docker Compose file for the Beacon v2 API MongoDB database.
- docker-compose.yml: Docker Compose file for the Beacon v2 API server and associated services.

## Instructions

### Beacon Data Tools

1. Check out the repository of the reference implementation of Beacon tools: https://github.com/EGA-archive/beacon2-ri-tools.
2. Follow the instructions to set up the external tools and configure the project.
3. Build and run the Docker container for the Beacon v2 Tools.


### Beacon Server (API)

1. First, check out the repository of the reference implementation: https://github.com/EGA-archive/beacon2-ri-api.
2. Follow the instructions in the provided README file for running the server.
3. After following the provided instructions, ensure that you have automated the initial data-import step by modifying the Docker Compose file and adding the `optional-init-data.sh` script to the `/deploy/mongo-init/` directory.
4. To include the YAML file provided in this repository, ensure that you build the Docker images before running the Docker Compose command. This will ensure that the images include the necessary modifications for the Beacon v2 API.

## Troubleshooting

If you encounter any issues related to file naming or case-insensitive filesystems, try renaming the problematic files or directories and restoring the original names using `git`.

If you encounter any issues related to CPU architecture or incompatible software, try modifying the Dockerfile or building the Docker container on a different system with the appropriate CPU architecture.

## License

The Beacon v2 Tools and Beacon v2 API are released under the Apache License 2.0.

