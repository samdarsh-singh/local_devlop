\echo
\echo 'Creating database schema for REMS:'

CREATE USER rems WITH PASSWORD 'remspassword';
CREATE SCHEMA AUTHORIZATION rems;

SET search_path TO 'rems';
SET SESSION ROLE rems;

\ir migrations.sql

RESET ROLE;
