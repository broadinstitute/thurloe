#!/bin/bash

liquibase --driver=com.mysql.jdbc.Driver \
     --classpath=${HOME}/.ivy2/cache/mysql/mysql-connector-java/jars/mysql-connector-java-5.1.35.jar \
     --changeLogFile=src/main/resources/liquibase/changelog.xml \
     --url="jdbc:mysql://localhost/thurloe_test" \
     --username=travis \
     --password="" \
     migrate
