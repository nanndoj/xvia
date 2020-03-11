# produce .elX dist tag on both centos and redhat
%define dist %(/usr/lib/rpm/redhat/dist.sh)

Name:               xroad-securityserver-is
Version:            %{xroad_version}
# release tag, e.g. 0.201508070816.el7 for snapshots and 1.el7 (for final releases)
Release:            %{rel}%{?snapshot}%{?dist}
Summary:            X-Road security server with Icelandic settings
BuildArch:          noarch
Group:              Applications/Internet
License:            MIT
Requires:           xroad-securityserver = %version-%release, xroad-addon-opmonitoring = %version-%release
Conflicts:          xroad-centralserver

%define src %{_topdir}/..

%description
This is meta package of X-Road security server with Icelandic settings

%clean

%prep

%build

%install

%files

%post

