#
# 1Science Busybox image
#

FROM dockerfile/java:oracle-java7
MAINTAINER 1Science <devops@1science.org>

RUN curl https://raw.githubusercontent.com/n8han/conscript/master/setup.sh | sh

ENV CONSCRIPT_OPTS="-Dfile.encoding=UTF-8"
ENV PATH=$PATH:/root/bin'

RUN cs sbt/sbt --branch 0.13.5

CMD ["/bin/bash"]

