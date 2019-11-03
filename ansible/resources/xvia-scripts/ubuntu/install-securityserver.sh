#!/usr/bin/env bash
# OS: Ubuntu
# Module: Security Server

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

# Install X-Road proxy
DEBIAN_FRONTEND=noninteractive sudo dpkg -i $DIRNAME/xroad-proxy_*.deb
sudo dpkg -i $DIRNAME/xroad-addon-metaservices_*.deb
sudo dpkg -i $DIRNAME/xroad-addon-messagelog_*.deb
sudo dpkg -i $DIRNAME/xroad-monitor_*.deb
sudo dpkg -i $DIRNAME/xroad-addon-proxymonitor_*.deb
sudo dpkg -i $DIRNAME/xroad-addon-wsdlvalidator_*.deb

# Install Support for Operational Monitoring
sudo dpkg -i $DIRNAME/xroad-opmonitor_*.deb
sudo dpkg -i $DIRNAME/xroad-addon-opmonitoring_*.deb
