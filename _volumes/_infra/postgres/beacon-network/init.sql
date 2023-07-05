\echo
\echo 'Creating database schema for BEACON-NETWORK:'

CREATE USER beacon_network WITH PASSWORD 'beacon-network-password';
CREATE SCHEMA AUTHORIZATION beacon_network;

SET SESSION ROLE beacon_network;
SET search_path TO 'beacon_network';

\ir ddl.sql

RESET ROLE;
