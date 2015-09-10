#!/bin/bash

liquibase --driver=com.mysql.jdbc.Driver \
     --classpath=/Users/chrisl/Downloads/mysql-connector-java-5.1.36/mysql-connector-java-5.1.36-bin.jar \
     --changeLogFile=/Users/chrisl/IdeaProjects/thurloe/src/main/resources/liquibase/changelog.xml \
     --url="jdbc:mysql://localhost/example" \
     --username=root \
     --password="" \
     migrate
