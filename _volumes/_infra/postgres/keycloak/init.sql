\echo
\echo 'Creating database schema for Keycloak:'

CREATE USER keycloak WITH PASSWORD 'keycloakpassword';
CREATE SCHEMA AUTHORIZATION keycloak;
