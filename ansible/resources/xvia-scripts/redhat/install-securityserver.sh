#!/usr/bin/env bash
# OS: Redhat / CentOS
# Module: Central Server

DIRNAME=$(cd `dirname $0` && pwd)

sudo yum -y install nginx-light
sudo yum -y install postgresql-server postgresql-contrib

sudo yum -y --nogpgcheck localinstall $DIRNAME/xroad-base-*.rpm

sudo yum -y --nogpgcheck localinstall $DIRNAME/xroad-nginx-*.rpm
sudo yum -y --nogpgcheck localinstall $DIRNAME/xroad-confclient-*.rpm
sudo yum -y --nogpgcheck localinstall $DIRNAME/xroad-jetty9-*.rpm
sudo yum -y --nogpgcheck localinstall $DIRNAME/xroad-signer-*.rpm

# Install X-Road proxy
sudo yum -y --nogpgcheck localinstall $DIRNAME/xroad-proxy-*.rpm
sudo yum -y --nogpgcheck localinstall $DIRNAME/xroad-addon-metaservices-*.rpm
sudo yum -y --nogpgcheck localinstall $DIRNAME/xroad-addon-messagelog-*.rpm
sudo yum -y --nogpgcheck localinstall $DIRNAME/xroad-monitor-*.rpm
sudo yum -y --nogpgcheck localinstall $DIRNAME/xroad-addon-proxymonitor-*.rpm
sudo yum -y --nogpgcheck localinstall $DIRNAME/xroad-addon-wsdlvalidator-*.rpm
sudo yum -y --nogpgcheck localinstall $DIRNAME/xroad-jetty9-*.rpm


#Install Support for Operational Monitoring
sudo yum -y --nogpgcheck localinstall $DIRNAME/xroad-opmonitor-*.rpm
sudo yum -y --nogpgcheck localinstall $DIRNAME/xroad-addon-opmonitoring-*.rpm

sudo service xroad-proxy restart

