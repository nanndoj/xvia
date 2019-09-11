#!/usr/bin/env bash
# OS: Redhat / CentOS
# Module: Central Server

DIRNAME=$(cd `dirname $0` && pwd)

sudo yum -y install nginx-light
sudo yum -y install postgresql-server postgresql-contrib

#Install X-Road Center
yum --nogpgcheck localinstall xroad-centralserver*.rpm
