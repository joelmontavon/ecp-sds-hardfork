# PostgreSQL database setup for RHEL systems

This document lists steps required to create the PostgreSQL database and roles necessary for the SDS to operate.

1. `sudo yum install postgresql-server`
2. `sudo postgresql-setup --initdb`
3. `sudo systemctl enable postgresql.service`
4. `sudo systemctl start postgresql.service`
5. `sudo -i -u postgres psql`
   1. `CREATE ROLE ecpsds_admin WITH NOLOGIN NOSUPERUSER INHERIT NOCREATEDB NOCREATEROLE NOREPLICATION;`
   2. `COMMENT ON ROLE ecpsds_admin IS 'eCarePlan Supplemental Data Store Administrator Role';`
   3. `CREATE ROLE ecpsds_owner WITH LOGIN NOSUPERUSER INHERIT NOCREATEDB NOCREATEROLE NOREPLICATION PASSWORD 'change_ME!';`
   4. `GRANT ecpsds_admin TO ecpsds_owner;`
   5. `CREATE DATABASE ecpsds WITH OWNER = ecpsds_owner ENCODING = 'UTF8' TABLESPACE = pg_default CONNECTION LIMIT = -1 IS_TEMPLATE = False;`
   6. `ALTER DEFAULT PRIVILEGES FOR ROLE postgres GRANT ALL ON TABLES TO ecpsds_admin;`
6. Enable password authentication for local socket access (see https://stackoverflow.com/questions/18664074/getting-error-peer-authentication-failed-for-user-postgres-when-trying-to-ge for details)
   1. `sudo vim /var/lib/pgsql/data/pg_hba.conf`
   2. Find the line `local all all peer` and change it to `local all all password`.
   3. Save the file and quit
   4. `sudo systemctl restart postgresql.service`
7. `psql -d ecpsds -U ecpsds_owner -W`