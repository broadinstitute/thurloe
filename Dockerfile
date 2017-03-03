FROM openjdk:8

# Thurloe's HTTP Port
EXPOSE 8000

RUN mkdir /thurloe
COPY ./thurloe*.jar /thurloe

# Start up Thurloe
CMD java $JAVA_OPTS -jar $(find /rawls -name 'thurloe*.jar')

# These next 4 commands are for enabling SSH to the container.
# id_rsa.pub is referenced below, but this should be any public key
# that you want to be added to authorized_keys for the root user.
# Copy the public key into this directory because ADD cannot reference
# Files outside of this directory

#EXPOSE 22
#RUN rm -f /etc/service/sshd/down
#ADD id_rsa.pub /tmp/id_rsa.pub
#RUN cat /tmp/id_rsa.pub >> /root/.ssh/authorized_keys
