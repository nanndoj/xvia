# Central server cluster setup

This ansible playbook configures a master (1) - slave (n) central server cluster. Security servers can download identical configuration from any of the public addresses published in a configuration anchor file.  

The playbook has been tested in GCP using CentOS 7, and Ubuntu 18.04 Minimal running default X-Road central server installation. Other environments might require modifications to the playbook.

## Prerequisites

* At least two central server nodes to be configured.
* The clocks of the cluster nodes are synchronized using e.g. NTP.
* At least one database host must be specified.
* The first database host will be configured as master. 

All the servers in a cluster should have the same operating system (Ubuntu 18.04 or RHEL 7). The setup also assumes that the servers are in the same subnet. If that is not the case, one needs to modify master's pg_hba.nconf so that it accepts replication configurations from the correct network(s).

## Set up SSL keys certificates for PostgreSQL replication connections

Create a CA certificate and store it in PEM format as ca.crt in the "ca" folder. Create TLS key and certificate (PEM) signed by the CA for each node and store those as ca/"nodename"/server.key and ca/"nodename"/server.crt. The server keys must not have a passphrase, but one can and should use ansible-vault to protect
the keys.

Note that the common name (CN) part of the certificate subject's DN must be the *nodename* defined in the host inventory file.

The ca directory contains two scripts that can be used to generate the keys and certificates.
* init.sh creates a CA key and self-signed certificate.
* add-node.sh creates a key and a certificate signed by the CA.

## Running the playbook

Remember to back up the servers before proceeding.

```
ansible-playbook --ask-vault-pass -i hosts/cluster-example.txt xroad_cs_cluster.yml
```
If testing the setup in a lxd container:
```
ansible-playbook --ask-vault-pass -c lxd --become-method=su -i hosts/example.txt xroad_cs_cluster.yml
```

The playbook does the following operations
* creates the db.properties pointing to the database hosts
* sets up the first database host as master
* sets up the other databes as slave
