#
# Scala and sbt Dockerfile part based on
# https://github.com/hseeberger/scala-sbt
#

# Pull base image
FROM openjdk:8u181

# Env variables
ENV SCALA_VERSION 2.12.7
ENV SBT_VERSION 1.2.4


# Scala expects this file
RUN touch /usr/lib/jvm/java-8-openjdk-amd64/release

# Install Scala
## Piping curl directly in tar
RUN \
  curl -fsL https://downloads.typesafe.com/scala/$SCALA_VERSION/scala-$SCALA_VERSION.tgz | tar xfz - -C /root/ && \
  echo >> /root/.bashrc && \
  echo "export PATH=~/scala-$SCALA_VERSION/bin:$PATH" >> /root/.bashrc

# Install sbt
RUN \
  curl -L -o sbt-$SBT_VERSION.deb https://dl.bintray.com/sbt/debian/sbt-$SBT_VERSION.deb && \
  dpkg -i sbt-$SBT_VERSION.deb && \
  rm sbt-$SBT_VERSION.deb && \
  apt-get update && \
  apt-get install -y sbt && \
  sbt sbtVersion

ENV PLAY_APP_NAME aplus
ENV PLAY_APP_DIR /var/www/$PLAY_APP_NAME
RUN mkdir -p $PLAY_APP_DIR
COPY build.sbt $PLAY_APP_DIR/
COPY app $PLAY_APP_DIR/app/
COPY conf $PLAY_APP_DIR/conf/
COPY public $PLAY_APP_DIR/public/
COPY data $PLAY_APP_DIR/data/
COPY project/*.properties project/*.sbt project/*.scala $PLAY_APP_DIR/project/

WORKDIR $PLAY_APP_DIR
ENV HOME $PLAY_APP_DIR
RUN sbt clean stage
RUN chmod 554 $PLAY_APP_DIR/target/universal/stage/bin/$PLAY_APP_NAME
RUN chmod 774 $PLAY_APP_DIR/target/universal/stage/

EXPOSE 9000
CMD ["sh", "-c", "$PLAY_APP_DIR/target/universal/stage/bin/$PLAY_APP_NAME $OPTIONS"]
