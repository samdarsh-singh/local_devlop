# REMS with Docker Compose

This repository contains a Docker Compose setup for REMS (Resource Entitlement Management System). It includes the changes suggested to improve maintainability and flexibility.

## Changes

1. Merged three separate docker-compose files into one base file (`docker-compose.yml`) and one override file (`docker-compose.override.yml`).
2. Updated the version of the `docker-compose.yml` file to 3.x for better compatibility and access to newer features.
3. Used environment variables for sensitive information such as database credentials, instead of hard-coding them in the `docker-compose.yml` file.

## Usage

Resource Entitlement Management System (REMS) is a tool for managing access rights to resources, such as research datasets.

Applicants can use their federated user IDs to log in to REMS, fill in the data access application and agree to the dataset's terms of use. The REMS system then circulates the application to the resource owner or designated representative for approval. REMS also produces the necessary reports on the applications and the granted data access rights.

REMS is a Clojure+ClojureScript Single Page App.

REMS is developed by a team at CSC – IT Center for Science.

You can try out REMS using the publicly available demo instance at https://rems-demo.rahtiapp.fi.


### Development

1. Create a `.env` file in the project root based on the `.env.example`file to store sensitive information: `cp .env.example .env`
2. Update the `.env` file with your specific values, such as database credentials or any other environment-specific configurations.
3. Run the following command to start the REMS application and its dependencies using the base and override configurations: `docker-compose -f docker-compose.yml -f docker-compose.override.yml up`

This command will use the `docker-compose.yml` file as the base configuration and apply the configurations from the `docker-compose.override.yml` file on top of it.

### Production

For a production environment, consider using a reverse proxy like Nginx to handle SSL/TLS termination and serve static files more efficiently. You may also want to adjust the memory reservations and limits based on your specific requirements.

## Custom Configuration Options

You can create custom configurations for different environments, such as development, testing, and production. You can store these configurations in separate files and manage them accordingly.

1. `dev-config.edn`: This file contains configuration options for the development environment. It includes settings like database URLs, search index path, authentication method, OpenID Connect settings, extra pages, theme paths, and various other settings specific to the development environment.
2. `empty-config.edn`: This file is meant to serve as a placeholder or starting point for creating new custom configurations.
3. `test-config.edn`: This file contains configuration options for the testing environment. It has similar settings to the dev-config.edn, such as database URLs, search index path, authentication method, extra pages, and other settings. However, it also includes some settings specific to the testing environment, such as test database URL and attachment max size.
4. `simple-config.edn`: This file contains a minimal configuration with only a few options specified, such as the port, database URL, search index path, authentication method, public URL, and extra stylesheets. This configuration could be a starting point for creating a more complex configuration or for testing purposes.

## Monitoring and Optimization

Monitor the performance of your REMS installation and adjust memory reservation and memory limits based on actual usage patterns to optimize resource allocation. This can help you better utilize your resources and avoid potential performance bottlenecks.
