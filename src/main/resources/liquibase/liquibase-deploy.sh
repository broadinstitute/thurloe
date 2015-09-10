#!/bin/bash

liquibase --driver=com.mysql.jdbc.Driver \
     --classpath
     --changeLogFile=src/main/resources/liquibase/changelog.xml \
     --url="jdbc:mysql://localhost/example" \
     --username=root \
     --password="" \
     migrate

#   Is this line needed??
#      --classpath=/path/to/classes \
