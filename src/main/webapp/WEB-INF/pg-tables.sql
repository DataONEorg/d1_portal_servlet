/*
 Creates everything for the OA4MP = OAuth for MyProxy PostgreSQL server database.
 pipe it into psql or just cut and paste it

 Edit the following values to be what you want. Be sure to update your configuration file.

 */
\set cilogonDatabase csd
\set cilogonSchema csd_portal
\set cilogonTransactionTable portal_transactions
\set cilogonServerUser cilogon
\set cilogonServerUserPassword '\'cilogon\''

/*
  Nothing needs to be edited from here down, unless you have a very specific reason to do so.
 */

\c :cilogonDatabase

DROP SCHEMA IF EXISTS :cilogonSchema CASCADE;
\c postgres
DROP DATABASE IF EXISTS :cilogonDatabase;
DROP USER IF EXISTS :cilogonServerUser;

CREATE DATABASE :cilogonDatabase;
\c :cilogonDatabase
CREATE SCHEMA :cilogonSchema;

CREATE USER :cilogonServerUser with PASSWORD :cilogonServerUserPassword;

create table :cilogonSchema.:cilogonTransactionTable  (
   temp_token text NOT NULL,
   temp_cred_ss text,
   certrequest bytea,
   oauth_verifier text,
   access_token text,
   access_token_ss text,
   certificate text,
   redirect_uri text,
   private_key bytea,
   identifier text,
   complete boolean);

CREATE UNIQUE INDEX trans_ndx ON :cilogonSchema.:cilogonTransactionTable (temp_token);

GRANT ALL PRIVILEGES ON DATABASE :cilogonDatabase TO :cilogonServerUser;
GRANT ALL PRIVILEGES ON SCHEMA  :cilogonSchema TO :cilogonServerUser;
GRANT ALL PRIVILEGES ON TABLE  :cilogonSchema.:cilogonTransactionTable TO :cilogonServerUser;

commit;