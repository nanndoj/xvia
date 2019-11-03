#!/usr/bin/env bash
# OS: Ubuntu
# Module: Central Server

DIRNAME=$(cd `dirname $0` && pwd)

sudo apt-get --assume-yes install nginx-light
sudo apt-get --assume-yes install postgresql
sudo apt-get --assume-yes install postgresql-contrib
sudo dpkg -i $DIRNAME/xroad-base_*.deb
sudo apt-get --assume-yes install -f
sudo dpkg -i $DIRNAME/xroad-nginx_*.deb
sudo dpkg -i $DIRNAME/xroad-confclient_*.deb
sudo dpkg -i $DIRNAME/xroad-jetty9_*.deb
sudo dpkg -i $DIRNAME/xroad-signer_*.deb

#Install X-Road Center
DEBIAN_FRONTEND=noninteractive sudo dpkg -i $DIRNAME/xroad-center_*.deb
sudo dpkg -i $DIRNAME/xroad-centralserver-monitoring_*.deb
sudo apt-get --assume-yes install -f
